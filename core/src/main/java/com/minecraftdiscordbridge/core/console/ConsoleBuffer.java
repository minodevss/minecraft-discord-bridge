package com.minecraftdiscordbridge.core.console;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

final class ConsoleBuffer {
    static final int CAPACITY = 2000;

    private final BlockingQueue<ConsoleLine> lines;
    private final AtomicLong dropped = new AtomicLong();

    ConsoleBuffer(int capacity) {
        this.lines = new ArrayBlockingQueue<>(capacity);
    }

    void offer(ConsoleLine line) {
        if (!lines.offer(line)) {
            dropped.incrementAndGet();
        }
    }

    List<ConsoleLine> drain(int max) {
        List<ConsoleLine> drained = new ArrayList<>();
        lines.drainTo(drained, max);
        return drained;
    }

    long takeDropped() {
        return dropped.getAndSet(0);
    }
}
