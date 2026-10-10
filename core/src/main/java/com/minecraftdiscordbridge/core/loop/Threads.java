package com.minecraftdiscordbridge.core.loop;

import static com.minecraftdiscordbridge.core.log.Log.LOGGER;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public final class Threads {
    private Threads() {}

    public static ThreadFactory named(String role) {
        AtomicInteger counter = new AtomicInteger();
        return task -> {
            Thread thread = new Thread(task, "MinecraftDiscordBridge " + role + " #" + counter.incrementAndGet());
            thread.setDaemon(true);
            thread.setUncaughtExceptionHandler(
                    (failed, error) -> LOGGER.error("Unexpected error on {}", failed.getName(), error));
            return thread;
        };
    }
}
