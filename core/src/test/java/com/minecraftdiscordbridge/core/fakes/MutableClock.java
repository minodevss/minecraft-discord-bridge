package com.minecraftdiscordbridge.core.fakes;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;

public final class MutableClock implements InstantSource {
    private volatile Instant now;

    public MutableClock(Instant start) {
        this.now = start;
    }

    @Override
    public Instant instant() {
        return now;
    }

    public void advance(Duration duration) {
        now = now.plus(duration);
    }
}
