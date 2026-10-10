package com.minecraftdiscordbridge.core.discord;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

public interface WebhookRepository {
    CompletionStage<Optional<WebhookCredentials>> find(DiscordId channelId);

    CompletionStage<Void> save(DiscordId channelId, WebhookCredentials webhook);

    CompletionStage<Void> forget(DiscordId channelId);
}
