package com.minecraftdiscordbridge.core.loop;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;

public final class IoExecutor implements AutoCloseable {
    private final ExecutorService executor;
    private final Executor loop;

    public IoExecutor(Executor loop) {
        this(loop, "I/O");
    }

    private IoExecutor(Executor loop, String role) {
        this.executor = Executors.newSingleThreadExecutor(Threads.named(role));
        this.loop = loop;
    }

    public static IoExecutor forLoop(CoreLoop loop, String role) {
        return new IoExecutor(loop::control, role);
    }

    public <T> CompletableFuture<T> completeOnIo(Task<T> task) {
        CompletableFuture<T> result = new CompletableFuture<>();
        start(result, () -> result.complete(task.run()));
        return result;
    }

    public CompletableFuture<Void> completeOnIo(Action action) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        start(result, () -> {
            action.run();
            result.complete(null);
        });
        return result;
    }

    public <T> void completeOnLoop(Task<T> task, Consumer<T> done, Consumer<Throwable> failed) {
        var unused = completeOnIo(task)
                .whenComplete((value, failure) -> loop.execute(() -> {
                    if (failure == null) {
                        done.accept(value);
                    } else {
                        failed.accept(failure);
                    }
                }));
    }

    public void completeOnLoop(Action action, Runnable done, Consumer<Throwable> failed) {
        var unused = completeOnIo(action)
                .whenComplete((value, failure) -> loop.execute(() -> {
                    if (failure == null) {
                        done.run();
                    } else {
                        failed.accept(failure);
                    }
                }));
    }

    private void start(CompletableFuture<?> result, Action work) {
        try {
            executor.execute(() -> {
                try {
                    work.run();
                } catch (IOException | RuntimeException failure) {
                    result.completeExceptionally(failure);
                }
            });
        } catch (RejectedExecutionException closed) {
            result.completeExceptionally(closed);
        }
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }

    @FunctionalInterface
    public interface Task<T> {
        T run() throws IOException;
    }

    @FunctionalInterface
    public interface Action {
        void run() throws IOException;
    }
}
