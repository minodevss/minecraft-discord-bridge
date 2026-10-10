package com.minecraftdiscordbridge.core.storage;

import com.minecraftdiscordbridge.core.discord.DiscordId;
import com.minecraftdiscordbridge.core.discord.WebhookCredentials;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

public final class WebhookStore {
    private final StoreFile file;
    private volatile Map<DiscordId, WebhookCredentials> byChannel;

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

    public Optional<WebhookCredentials> forChannel(DiscordId channelId) {
        return Optional.ofNullable(byChannel.get(channelId));
    }

    public synchronized void put(DiscordId channelId, WebhookCredentials webhook) throws IOException {
        Map<DiscordId, WebhookCredentials> next = new HashMap<>(byChannel);
        next.put(channelId, webhook);
        replace(next);
    }

    public synchronized void remove(DiscordId channelId) throws IOException {
        if (!byChannel.containsKey(channelId)) {
            return;
        }
        Map<DiscordId, WebhookCredentials> next = new HashMap<>(byChannel);
        next.remove(channelId);
        replace(next);
    }

    private void replace(Map<DiscordId, WebhookCredentials> next) throws IOException {
        Map<String, WebhookJson> entries = new TreeMap<>();
        next.forEach((channel, webhook) ->
                entries.put(channel.toString(), new WebhookJson(webhook.id().toString(), webhook.token())));
        file.save(new WebhooksJson(StoreFile.FORMAT, entries));
        byChannel = Map.copyOf(next);
    }

    private static Map<DiscordId, WebhookCredentials> decode(WebhooksJson json) {
        Map<DiscordId, WebhookCredentials> byChannel = new HashMap<>();
        json.webhooks()
                .forEach((channel, entry) -> byChannel.put(
                        DiscordId.require(channel, "channelId"),
                        new WebhookCredentials(DiscordId.require(entry.id(), "webhook id"), entry.token())));
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
