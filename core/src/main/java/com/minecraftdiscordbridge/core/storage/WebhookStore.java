package com.minecraftdiscordbridge.core.storage;

import com.minecraftdiscordbridge.core.discord.DiscordId;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

public final class WebhookStore {
    private final StoreFile file;
    private volatile Map<DiscordId, Webhook> byChannel;

    private WebhookStore(StoreFile file) {
        this.file = file;
        this.byChannel = file.load(WebhooksJson.class, WebhookStore::decode, Map.of());
    }

    public static WebhookStore open(Path dataDirectory) {
        return new WebhookStore(StoreFile.ownerOnly(dataDirectory, "webhooks.json", "Webhooks"));
    }

    public boolean readOnly() {
        return file.readOnly();
    }

    public Optional<Webhook> forChannel(DiscordId channelId) {
        return Optional.ofNullable(byChannel.get(channelId));
    }

    public synchronized void put(DiscordId channelId, Webhook webhook) throws IOException {
        Map<DiscordId, Webhook> next = new HashMap<>(byChannel);
        next.put(channelId, webhook);
        replace(next);
    }

    public synchronized void remove(DiscordId channelId) throws IOException {
        if (!byChannel.containsKey(channelId)) {
            return;
        }
        Map<DiscordId, Webhook> next = new HashMap<>(byChannel);
        next.remove(channelId);
        replace(next);
    }

    private void replace(Map<DiscordId, Webhook> next) throws IOException {
        Map<String, WebhookJson> entries = new TreeMap<>();
        next.forEach((channel, webhook) ->
                entries.put(channel.toString(), new WebhookJson(webhook.id().toString(), webhook.token())));
        file.save(new WebhooksJson(StoreFile.FORMAT, entries));
        byChannel = Map.copyOf(next);
    }

    private static Map<DiscordId, Webhook> decode(WebhooksJson json) {
        Map<DiscordId, Webhook> byChannel = new HashMap<>();
        json.webhooks()
                .forEach((channel, entry) -> byChannel.put(
                        DiscordId.require(channel, "channelId"),
                        new Webhook(DiscordId.require(entry.id(), "webhook id"), entry.token())));
        return Map.copyOf(byChannel);
    }

    record WebhooksJson(int version, Map<String, WebhookJson> webhooks) implements Versioned {}

    record WebhookJson(String id, String token) {
        @Override
        public String toString() {
            return "WebhookJson[id=" + id + ", token=<hidden>]";
        }
    }
}
