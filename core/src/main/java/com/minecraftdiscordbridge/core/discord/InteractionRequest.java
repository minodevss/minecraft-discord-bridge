package com.minecraftdiscordbridge.core.discord;

import java.util.Map;

public record InteractionRequest(
        long interactionId,
        String command,
        Map<String, String> options,
        long guildId,
        long channelId,
        long userId,
        String userDisplayName,
        String locale) {
    public InteractionRequest {
        options = Map.copyOf(options);
    }
}
