package com.minecraftdiscordbridge.core.discord;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

public interface CommandHashRepository {
    CompletionStage<Optional<String>> find(DiscordId guildId);

    CompletionStage<Void> save(DiscordId guildId, String hash);
}
