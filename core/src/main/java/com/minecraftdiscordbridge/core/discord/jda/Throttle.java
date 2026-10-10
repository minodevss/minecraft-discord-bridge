package com.minecraftdiscordbridge.core.discord.jda;

import com.minecraftdiscordbridge.core.loop.Scheduler;
import java.time.Duration;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import org.jspecify.annotations.Nullable;

final class Throttle<T> {
    private final Duration interval;
    private final LongSupplier nanoClock;
    private final Scheduler scheduler;
    private final Consumer<T> apply;
    private @Nullable T applied;
    private @Nullable T pending;
    private long appliedAt;
    private boolean scheduled;

    Throttle(Duration interval, LongSupplier nanoClock, Scheduler scheduler, Consumer<T> apply) {
        this.interval = interval;
        this.nanoClock = nanoClock;
        this.scheduler = scheduler;
        this.apply = apply;
    }

    void submit(T value) {
        T latest = pending != null ? pending : applied;
        if (value.equals(latest)) {
            return;
        }
        pending = value;
        if (scheduled) {
            return;
        }
        long waitNanos = applied == null ? 0 : appliedAt + interval.toNanos() - nanoClock.getAsLong();
        if (waitNanos <= 0) {
            flush();
            return;
        }
        scheduled = true;
        scheduler.later(Duration.ofNanos(waitNanos), this::flush);
    }

    void reapply() {
        T current = applied;
        if (current != null) {
            apply.accept(current);
        }
    }

    private void flush() {
        scheduled = false;
        T value = pending;
        pending = null;
        if (value == null || value.equals(applied)) {
            return;
        }
        applied = value;
        appliedAt = nanoClock.getAsLong();
        apply.accept(value);
    }
}
