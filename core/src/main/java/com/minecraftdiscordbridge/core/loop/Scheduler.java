package com.minecraftdiscordbridge.core.loop;

import java.time.Duration;

@FunctionalInterface
public interface Scheduler {
    void later(Duration delay, Runnable task);
}
