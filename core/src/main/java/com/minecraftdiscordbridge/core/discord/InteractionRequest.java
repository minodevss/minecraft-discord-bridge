package com.minecraftdiscordbridge.core.discord;

import java.util.Map;
import org.jspecify.annotations.Nullable;

public record InteractionRequest(
        String command,
        @Nullable String subcommand,
        Map<String, String> options,
        long guildId,
        long channelId,
        long userId,
        String userDisplayName,
        boolean canManageServer,
        String locale) {
    public InteractionRequest {
        options = Map.copyOf(options);
    }
}
