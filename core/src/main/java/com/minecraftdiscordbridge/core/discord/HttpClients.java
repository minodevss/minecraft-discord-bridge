package com.minecraftdiscordbridge.core.discord;

import com.minecraftdiscordbridge.core.loop.Threads;
import java.time.Duration;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;

final class HttpClients {
    private HttpClients() {}

    static OkHttpClient create(RateLimitCounter rateLimits) {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                0, Integer.MAX_VALUE, 60, TimeUnit.SECONDS, new SynchronousQueue<>(), Threads.named("HTTP"));
        return new OkHttpClient.Builder()
                .dispatcher(new Dispatcher(executor))
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(10))
                .writeTimeout(Duration.ofSeconds(10))
                .callTimeout(Duration.ofSeconds(15))
                .addInterceptor(rateLimits)
                .build();
    }

    static void close(OkHttpClient http) {
        http.dispatcher().cancelAll();
        http.dispatcher().executorService().shutdownNow();
        http.connectionPool().evictAll();
    }
}
