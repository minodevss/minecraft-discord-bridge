package com.minecraftdiscordbridge.core.storage;

import com.minecraftdiscordbridge.core.discord.DiscordId;
import com.minecraftdiscordbridge.core.discord.WebhookCredentials;
import com.minecraftdiscordbridge.core.discord.WebhookRepository;
import com.minecraftdiscordbridge.core.loop.IoExecutor;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

public final class StoredWebhooks implements WebhookRepository {
    private final WebhookStore store;
    private final IoExecutor io;

    public StoredWebhooks(WebhookStore store, IoExecutor io) {
        this.store = store;
        this.io = io;
    }

    @Override
    public CompletionStage<Optional<WebhookCredentials>> find(long channelId) {
        return io.completeOnIo(() -> store.forChannel(new DiscordId(channelId)));
    }

    @Override
    public CompletionStage<Void> save(long channelId, WebhookCredentials webhook) {
        return io.completeOnIo(() -> store.put(new DiscordId(channelId), webhook));
    }

    @Override
    public CompletionStage<Void> forget(long channelId) {
        return io.completeOnIo(() -> store.remove(new DiscordId(channelId)));
    }
}
