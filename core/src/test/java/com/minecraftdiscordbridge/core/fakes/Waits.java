package com.minecraftdiscordbridge.core.fakes;

import static org.assertj.core.api.Assertions.fail;

import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

public final class Waits {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final Duration QUIET = Duration.ofMillis(300);

    private Waits() {}

    public static <T> T take(BlockingQueue<T> queue) {
        try {
            T value = queue.poll(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            if (value == null) {
                fail("Nothing arrived within " + TIMEOUT.toSeconds() + " seconds");
            }
            return value;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

    public static <T> void nothingIn(BlockingQueue<T> queue) {
        try {
            T value = queue.poll(QUIET.toMillis(), TimeUnit.MILLISECONDS);
            if (value != null) {
                fail("Expected nothing, got " + value);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

    public static void until(String what, BooleanSupplier condition) {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                fail("Timed out waiting for " + what);
            }
            pause(what);
        }
    }

    public static void stays(String what, BooleanSupplier condition) {
        long end = System.nanoTime() + QUIET.toNanos();
        while (System.nanoTime() < end) {
            if (!condition.getAsBoolean()) {
                fail("Expected " + what + " to stay true");
            }
            pause(what);
        }
    }

    private static void pause(String what) {
        try {
            Thread.sleep(5);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            fail("Interrupted waiting for " + what);
        }
    }
}
