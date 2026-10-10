package com.minecraftdiscordbridge.core.discord.jda;

import com.minecraftdiscordbridge.core.discord.DiscordId;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import okhttp3.Interceptor;
import okhttp3.Response;
import org.jspecify.annotations.Nullable;

final class RateLimitCounter implements Interceptor {
    private final Map<DiscordId, LongAdder> byChannel = new ConcurrentHashMap<>();
    private final Map<DiscordId, DiscordId> webhookChannels = new ConcurrentHashMap<>();

    @Override
    public Response intercept(Interceptor.Chain chain) throws IOException {
        Response response = chain.proceed(chain.request());
        if (response.code() == 429) {
            record(chain.request().url().pathSegments());
        }
        return response;
    }

    void bindWebhook(DiscordId webhookId, DiscordId channelId) {
        webhookChannels.put(webhookId, channelId);
    }

    long count(DiscordId channelId) {
        LongAdder counter = byChannel.get(channelId);
        return counter == null ? 0 : counter.sum();
    }

    private void record(List<String> segments) {
        for (int i = 0; i + 1 < segments.size(); i++) {
            DiscordId channel = channelFor(segments.get(i), segments.get(i + 1));
            if (channel != null) {
                byChannel.computeIfAbsent(channel, id -> new LongAdder()).increment();
                return;
            }
        }
    }

    private @Nullable DiscordId channelFor(String resource, String id) {
        if (!resource.equals("channels") && !resource.equals("webhooks")) {
            return null;
        }
        DiscordId snowflake = DiscordId.parse(id).orElse(null);
        if (snowflake == null || resource.equals("channels")) {
            return snowflake;
        }
        return webhookChannels.get(snowflake);
    }
}
