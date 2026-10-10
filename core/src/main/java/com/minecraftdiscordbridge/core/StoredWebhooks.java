package com.minecraftdiscordbridge.core;

import com.minecraftdiscordbridge.core.discord.DiscordId;
import com.minecraftdiscordbridge.core.discord.WebhookCredentials;
import com.minecraftdiscordbridge.core.discord.WebhookRepository;
import com.minecraftdiscordbridge.core.loop.IoExecutor;
import com.minecraftdiscordbridge.core.storage.Webhook;
import com.minecraftdiscordbridge.core.storage.WebhookStore;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

final class StoredWebhooks implements WebhookRepository {
    private final WebhookStore store;
    private final IoExecutor io;

    StoredWebhooks(WebhookStore store, IoExecutor io) {
        this.store = store;
        this.io = io;
    }

    @Override
    public CompletionStage<Optional<WebhookCredentials>> find(long channelId) {
        return io.submit(() -> store.forChannel(new DiscordId(channelId))
                .map(webhook -> new WebhookCredentials(webhook.id().value(), webhook.token())));
    }

    @Override
    public CompletionStage<Void> save(long channelId, WebhookCredentials webhook) {
        return io.perform(() ->
                        store.put(new DiscordId(channelId), new Webhook(new DiscordId(webhook.id()), webhook.token())))
                .thenAccept(done -> {});
    }

    @Override
    public CompletionStage<Void> forget(long channelId) {
        return io.perform(() -> store.remove(new DiscordId(channelId))).thenAccept(done -> {});
    }
}
