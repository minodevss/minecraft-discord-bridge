package com.minecraftdiscordbridge.core.loop;

import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CoreLoop implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger("MinecraftDiscordBridge");
    private static final int CONTENT_CAPACITY = 1024;
    private static final long IDLE = 0;

    private final BlockingQueue<Runnable> control = new LinkedBlockingQueue<>();
    private final BlockingQueue<Runnable> content = new ArrayBlockingQueue<>(CONTENT_CAPACITY);
    private final Semaphore pending = new Semaphore(0);
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicLong taskStartedNanos = new AtomicLong(IDLE);
    private final AtomicLong reportedTaskNanos = new AtomicLong(IDLE);
    private final Duration slowTask;
    private final ScheduledExecutorService timer;
    private final Thread thread;
    private final ScheduledFuture<?> watchdog;
    private volatile boolean running = true;

    public CoreLoop() {
        this(Duration.ofSeconds(1));
    }

    CoreLoop(Duration slowTask) {
        this.slowTask = slowTask;
        this.timer = Executors.newSingleThreadScheduledExecutor(Threads.named("Timer"));
        this.thread = Threads.named("Core").newThread(this::run);
        this.thread.start();
        long period = Math.max(10, slowTask.toMillis() / 2);
        this.watchdog = timer.scheduleAtFixedRate(this::checkSlowTask, period, period, TimeUnit.MILLISECONDS);
    }

    public void control(Runnable task) {
        if (!running) {
            return;
        }
        control.add(task);
        pending.release();
    }

    public boolean content(Runnable task) {
        if (!running) {
            return false;
        }
        if (!content.offer(task)) {
            dropped.incrementAndGet();
            return false;
        }
        pending.release();
        return true;
    }

    public long takeDropped() {
        return dropped.getAndSet(0);
    }

    public ScheduledFuture<?> schedule(Duration delay, Runnable task) {
        return timer.schedule(() -> control(task), delay.toMillis(), TimeUnit.MILLISECONDS);
    }

    public ScheduledFuture<?> repeat(Duration period, Runnable task) {
        return timer.scheduleAtFixedRate(
                () -> control(task), period.toMillis(), period.toMillis(), TimeUnit.MILLISECONDS);
    }

    public boolean inLoop() {
        return Thread.currentThread().equals(thread);
    }

    @Override
    public void close() {
        running = false;
        pending.release();
        watchdog.cancel(false);
        timer.shutdownNow();
        try {
            thread.join(Duration.ofMillis(500).toMillis());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private void run() {
        while (running) {
            try {
                pending.acquire();
            } catch (InterruptedException interrupted) {
                return;
            }
            Runnable task = control.poll();
            if (task == null) {
                task = content.poll();
            }
            if (task != null) {
                execute(task);
            }
        }
    }

    private void execute(Runnable task) {
        taskStartedNanos.set(System.nanoTime());
        try {
            task.run();
        } catch (RuntimeException | LinkageError error) {
            LOGGER.error("A bridge task failed", error);
        } finally {
            taskStartedNanos.set(IDLE);
        }
    }

    private void checkSlowTask() {
        long started = taskStartedNanos.get();
        if (started == IDLE || started == reportedTaskNanos.get()) {
            return;
        }
        if (System.nanoTime() - started < slowTask.toNanos()) {
            return;
        }
        reportedTaskNanos.set(started);
        Throwable trace = new Throwable("Core loop task running for over " + slowTask.toMillis() + " ms");
        trace.setStackTrace(thread.getStackTrace());
        LOGGER.warn("The bridge core loop is blocked", trace);
    }
}
