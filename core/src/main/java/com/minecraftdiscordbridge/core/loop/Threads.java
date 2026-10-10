package com.minecraftdiscordbridge.core.loop;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Threads {
    private static final Logger LOGGER = LoggerFactory.getLogger("MinecraftDiscordBridge");

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
