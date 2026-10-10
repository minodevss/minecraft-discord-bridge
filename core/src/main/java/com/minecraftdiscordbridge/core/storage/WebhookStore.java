package com.minecraftdiscordbridge.core.storage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

public final class WebhookStore {
    private final StoreFile file;
    private volatile Map<String, Webhook> byChannel;

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

    public Optional<Webhook> forChannel(String channelId) {
        return Optional.ofNullable(byChannel.get(channelId));
    }

    public synchronized void put(String channelId, Webhook webhook) throws IOException {
        DiscordIds.require(channelId, "channelId");
        Map<String, Webhook> next = new HashMap<>(byChannel);
        next.put(channelId, webhook);
        replace(next);
    }

    public synchronized void remove(String channelId) throws IOException {
        if (!byChannel.containsKey(channelId)) {
            return;
        }
        Map<String, Webhook> next = new HashMap<>(byChannel);
        next.remove(channelId);
        replace(next);
    }

    private void replace(Map<String, Webhook> next) throws IOException {
        Map<String, WebhookJson> entries = new TreeMap<>();
        next.forEach((channel, webhook) -> entries.put(channel, new WebhookJson(webhook.id(), webhook.token())));
        file.save(new WebhooksJson(StoreFile.FORMAT, entries));
        byChannel = Map.copyOf(next);
    }

    private static Map<String, Webhook> decode(WebhooksJson json) {
        Map<String, Webhook> byChannel = new HashMap<>();
        json.webhooks().forEach((channel, entry) -> {
            DiscordIds.require(channel, "channelId");
            byChannel.put(channel, new Webhook(entry.id(), entry.token()));
        });
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
