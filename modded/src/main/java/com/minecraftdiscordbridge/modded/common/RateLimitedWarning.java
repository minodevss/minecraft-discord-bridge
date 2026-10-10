package com.minecraftdiscordbridge.modded.common;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class RateLimitedWarning {
    private static final Logger LOGGER = LoggerFactory.getLogger("MinecraftDiscordBridge");
    private static final long INTERVAL_NANOS = TimeUnit.MINUTES.toNanos(1);

    private final AtomicLong lastLogged = new AtomicLong(System.nanoTime() - INTERVAL_NANOS);

    void warn(String message, Throwable error) {
        long now = System.nanoTime();
        long last = lastLogged.get();
        if (now - last >= INTERVAL_NANOS && lastLogged.compareAndSet(last, now)) {
            LOGGER.warn(message, error);
        }
    }
}
