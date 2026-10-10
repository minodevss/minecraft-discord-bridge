package com.minecraftdiscordbridge.core.discord;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

public interface CommandHashRepository {
    CompletionStage<Optional<String>> find(long guildId);

    CompletionStage<Void> save(long guildId, String hash);
}
