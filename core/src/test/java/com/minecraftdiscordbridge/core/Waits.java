package com.minecraftdiscordbridge.core;

import static org.assertj.core.api.Assertions.fail;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

public final class Waits {
    private Waits() {}

    public static <T> T take(BlockingQueue<T> queue) {
        try {
            T value = queue.poll(5, TimeUnit.SECONDS);
            if (value == null) {
                fail("Nothing arrived within 5 seconds");
            }
            return value;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

    public static <T> void nothingIn(BlockingQueue<T> queue) {
        try {
            T value = queue.poll(300, TimeUnit.MILLISECONDS);
            if (value != null) {
                fail("Expected nothing, got " + value);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

    public static void until(String what, BooleanSupplier condition) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                fail("Timed out waiting for " + what);
            }
            try {
                Thread.sleep(5);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                fail("Interrupted waiting for " + what);
            }
        }
    }
}
