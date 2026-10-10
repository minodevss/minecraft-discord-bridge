package com.minecraftdiscordbridge.core.codes;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

public final class Attempts {
    public static final Duration WINDOW = Duration.ofMinutes(10);
    public static final int PER_USER = 5;
    public static final int OVERALL = 50;

    private final int perUser;
    private final int overall;
    private final Duration window;
    private final Map<Long, Deque<Instant>> byUser = new HashMap<>();
    private final Deque<Instant> all = new ArrayDeque<>();

    public Attempts(int perUser, int overall, Duration window) {
        this.perUser = perUser;
        this.overall = overall;
        this.window = window;
    }

    public static Attempts standard() {
        return new Attempts(PER_USER, OVERALL, WINDOW);
    }

    public boolean blocked(long userId, Instant now) {
        forgetBefore(now.minus(window));
        Deque<Instant> mine = byUser.get(userId);
        return all.size() >= overall || (mine != null && mine.size() >= perUser);
    }

    public void failed(long userId, Instant now) {
        forgetBefore(now.minus(window));
        byUser.computeIfAbsent(userId, id -> new ArrayDeque<>()).addLast(now);
        all.addLast(now);
    }

    private void forgetBefore(Instant cutoff) {
        dropOld(all, cutoff);
        byUser.values().removeIf(failures -> dropOld(failures, cutoff).isEmpty());
    }

    private static Deque<Instant> dropOld(Deque<Instant> failures, Instant cutoff) {
        while (!failures.isEmpty() && !failures.peekFirst().isAfter(cutoff)) {
            failures.removeFirst();
        }
        return failures;
    }
}
