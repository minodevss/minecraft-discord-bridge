package com.minecraftdiscordbridge.core.loop;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class CoreLoopTest {
    @Test
    void runsControlTasksBeforeQueuedContent() throws InterruptedException {
        try (CoreLoop loop = new CoreLoop()) {
            CountDownLatch gate = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(3);
            List<String> order = new CopyOnWriteArrayList<>();
            loop.control(() -> await(gate));
            loop.content(() -> record(order, "content", done));
            loop.control(() -> record(order, "control", done));
            loop.control(() -> record(order, "control2", done));
            gate.countDown();

            assertThat(done.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(order).containsExactly("control", "control2", "content");
        }
    }

    @Test
    void dropsContentWhenFullAndCountsIt() throws InterruptedException {
        try (CoreLoop loop = new CoreLoop()) {
            CountDownLatch gate = new CountDownLatch(1);
            loop.control(() -> await(gate));
            int accepted = 0;
            for (int i = 0; i < 1100; i++) {
                if (loop.content(() -> {})) {
                    accepted++;
                }
            }
            gate.countDown();

            assertThat(accepted).isEqualTo(1024);
            assertThat(loop.takeDropped()).isEqualTo(76);
            assertThat(loop.takeDropped()).isZero();
        }
    }

    @Test
    void keepsRunningAfterAFailingTask() throws InterruptedException {
        try (CoreLoop loop = new CoreLoop()) {
            CountDownLatch done = new CountDownLatch(1);
            loop.control(() -> {
                throw new IllegalStateException("boom");
            });
            loop.control(done::countDown);

            assertThat(done.await(2, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void runsScheduledTasksOnTheLoop() throws InterruptedException {
        try (CoreLoop loop = new CoreLoop()) {
            CountDownLatch done = new CountDownLatch(1);
            List<Boolean> onLoop = new CopyOnWriteArrayList<>();
            loop.later(Duration.ofMillis(20), () -> {
                onLoop.add(loop.inLoop());
                done.countDown();
            });

            assertThat(done.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(onLoop).containsExactly(true);
        }
    }

    private static void record(List<String> order, String name, CountDownLatch done) {
        order.add(name);
        done.countDown();
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
