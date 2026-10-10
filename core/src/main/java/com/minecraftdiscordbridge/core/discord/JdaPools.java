package com.minecraftdiscordbridge.core.discord;

import com.minecraftdiscordbridge.core.loop.Threads;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import net.dv8tion.jda.api.JDABuilder;

record JdaPools(
        ScheduledExecutorService gateway,
        ScheduledExecutorService rateLimitScheduler,
        ExecutorService rateLimitElastic,
        ExecutorService callback) {
    static JdaPools create() {
        ThreadPoolExecutor elastic = new ThreadPoolExecutor(
                0,
                8,
                60,
                TimeUnit.SECONDS,
                new SynchronousQueue<>(),
                Threads.named("RateLimit Elastic"),
                new ThreadPoolExecutor.CallerRunsPolicy());
        ThreadPoolExecutor callback = new ThreadPoolExecutor(
                1,
                1,
                0,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                Threads.named("Callback"),
                new ThreadPoolExecutor.DiscardPolicy());
        return new JdaPools(scheduler("Gateway"), scheduler("RateLimit"), elastic, callback);
    }

    private static ScheduledExecutorService scheduler(String role) {
        return new ScheduledThreadPoolExecutor(1, Threads.named(role), new ThreadPoolExecutor.DiscardPolicy());
    }

    JDABuilder applyTo(JDABuilder builder) {
        return builder.setGatewayPool(gateway, false)
                .setRateLimitScheduler(rateLimitScheduler, false)
                .setRateLimitElastic(rateLimitElastic, false)
                .setCallbackPool(callback, false);
    }

    void shutdownNow() {
        for (ExecutorService pool : List.of(gateway, rateLimitScheduler, rateLimitElastic, callback)) {
            var unused = pool.shutdownNow();
        }
    }
}
