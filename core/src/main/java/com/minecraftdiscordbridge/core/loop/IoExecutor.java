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
    private final Executor callbacks;

    public IoExecutor(Executor callbacks) {
        this(callbacks, "I/O");
    }

    private IoExecutor(Executor callbacks, String role) {
        this.executor = Executors.newSingleThreadExecutor(Threads.named(role));
        this.callbacks = callbacks;
    }

    public static IoExecutor forLoop(CoreLoop loop, String role) {
        return new IoExecutor(loop::control, role);
    }

    public <T> CompletableFuture<T> submit(Task<T> task) {
        CompletableFuture<T> result = new CompletableFuture<>();
        try {
            executor.execute(() -> {
                try {
                    result.complete(task.run());
                } catch (IOException | RuntimeException failure) {
                    result.completeExceptionally(failure);
                }
            });
        } catch (RejectedExecutionException closed) {
            result.completeExceptionally(closed);
        }
        return result;
    }

    public CompletableFuture<Boolean> perform(Action action) {
        return submit(() -> {
            action.run();
            return true;
        });
    }

    public <T> void call(Task<T> task, Consumer<T> done, Consumer<Throwable> failed) {
        var unused = submit(task)
                .whenComplete((value, failure) -> callbacks.execute(() -> {
                    if (failure == null) {
                        done.accept(value);
                    } else {
                        failed.accept(failure);
                    }
                }));
    }

    public void run(Action action, Runnable done, Consumer<Throwable> failed) {
        call(
                () -> {
                    action.run();
                    return true;
                },
                ignored -> done.run(),
                failed);
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
