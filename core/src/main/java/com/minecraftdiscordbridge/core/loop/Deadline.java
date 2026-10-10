package com.minecraftdiscordbridge.core.loop;

import java.time.Duration;

public record Deadline(long nanoTime) {
    public static Deadline after(Duration budget) {
        return new Deadline(System.nanoTime() + budget.toNanos());
    }

    public Deadline minus(Duration margin) {
        return new Deadline(nanoTime - margin.toNanos());
    }

    public Duration remaining() {
        return Duration.ofNanos(Math.max(0, nanoTime - System.nanoTime()));
    }
}
