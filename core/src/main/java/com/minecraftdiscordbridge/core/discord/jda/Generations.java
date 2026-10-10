package com.minecraftdiscordbridge.core.discord.jda;

import java.util.concurrent.atomic.AtomicInteger;

final class Generations {
    private final AtomicInteger current = new AtomicInteger();

    int next() {
        return current.incrementAndGet();
    }

    boolean isCurrent(int generation) {
        return current.get() == generation;
    }

    void retireAll() {
        current.incrementAndGet();
    }
}
