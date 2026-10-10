package com.minecraftdiscordbridge.core.discord.jda;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import okhttp3.Interceptor;
import okhttp3.Response;
import org.jspecify.annotations.Nullable;

final class RateLimitCounter implements Interceptor {
    private final Map<Long, LongAdder> byChannel = new ConcurrentHashMap<>();
    private final Map<Long, Long> webhookChannels = new ConcurrentHashMap<>();

    @Override
    public Response intercept(Interceptor.Chain chain) throws IOException {
        Response response = chain.proceed(chain.request());
        if (response.code() == 429) {
            record(chain.request().url().pathSegments());
        }
        return response;
    }

    void bindWebhook(long webhookId, long channelId) {
        webhookChannels.put(webhookId, channelId);
    }

    long count(long channelId) {
        LongAdder counter = byChannel.get(channelId);
        return counter == null ? 0 : counter.sum();
    }

    private void record(List<String> segments) {
        for (int i = 0; i + 1 < segments.size(); i++) {
            Long channel = channelFor(segments.get(i), segments.get(i + 1));
            if (channel != null) {
                byChannel.computeIfAbsent(channel, id -> new LongAdder()).increment();
                return;
            }
        }
    }

    private @Nullable Long channelFor(String resource, String id) {
        if (!resource.equals("channels") && !resource.equals("webhooks")) {
            return null;
        }
        Long snowflake = parse(id);
        if (snowflake == null) {
            return null;
        }
        return resource.equals("channels") ? snowflake : webhookChannels.get(snowflake);
    }

    private static @Nullable Long parse(String id) {
        try {
            return Long.parseUnsignedLong(id);
        } catch (NumberFormatException notASnowflake) {
            return null;
        }
    }
}
