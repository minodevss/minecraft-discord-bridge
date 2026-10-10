package com.minecraftdiscordbridge.core.discord;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ThrottleTest {
    private static final Duration INTERVAL = Duration.ofSeconds(15);

    private long now = 1_000_000_000L;
    private final List<Duration> delays = new ArrayList<>();
    private final List<Runnable> scheduled = new ArrayList<>();
    private final List<String> applied = new ArrayList<>();
    private final Throttle<String> throttle = new Throttle<>(
            INTERVAL,
            () -> now,
            (delay, task) -> {
                delays.add(delay);
                scheduled.add(task);
            },
            applied::add);

    @Test
    void appliesTheFirstValueRightAway() {
        throttle.submit("3 players online");

        assertThat(applied).containsExactly("3 players online");
    }

    @Test
    void skipsValuesThatDidNotChange() {
        throttle.submit("a");
        throttle.submit("a");

        assertThat(applied).containsExactly("a");
        assertThat(scheduled).isEmpty();
    }

    @Test
    void waitsForTheRestOfTheIntervalBeforeTheNextChange() {
        throttle.submit("a");
        now += Duration.ofSeconds(5).toNanos();

        throttle.submit("b");

        assertThat(applied).containsExactly("a");
        assertThat(delays).containsExactly(Duration.ofSeconds(10));
        runScheduled();
        assertThat(applied).containsExactly("a", "b");
    }

    @Test
    void appliesOnlyTheLatestOfSeveralChangesInOneInterval() {
        throttle.submit("a");
        throttle.submit("b");
        throttle.submit("c");

        runScheduled();

        assertThat(applied).containsExactly("a", "c");
        assertThat(delays).hasSize(1);
    }

    @Test
    void skipsAChangeThatWasUndoneBeforeTheIntervalEnded() {
        throttle.submit("a");
        throttle.submit("b");
        throttle.submit("a");

        runScheduled();

        assertThat(applied).containsExactly("a");
    }

    @Test
    void appliesRightAwayOnceTheIntervalHasPassed() {
        throttle.submit("a");
        now += INTERVAL.toNanos();

        throttle.submit("b");

        assertThat(applied).containsExactly("a", "b");
    }

    @Test
    void reappliesTheCurrentValue() {
        throttle.submit("a");

        throttle.reapply();

        assertThat(applied).containsExactly("a", "a");
    }

    private void runScheduled() {
        now += INTERVAL.toNanos();
        List<Runnable> tasks = List.copyOf(scheduled);
        scheduled.clear();
        tasks.forEach(Runnable::run);
    }
}
