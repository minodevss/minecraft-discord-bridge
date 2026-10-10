package com.minecraftdiscordbridge.core.discord;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

public interface WebhookRepository {
    CompletionStage<Optional<WebhookCredentials>> find(long channelId);

    CompletionStage<Void> save(long channelId, WebhookCredentials webhook);

    CompletionStage<Void> forget(long channelId);
}
