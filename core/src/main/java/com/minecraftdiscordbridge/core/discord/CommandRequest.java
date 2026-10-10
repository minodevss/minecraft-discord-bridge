package com.minecraftdiscordbridge.core.discord;

import java.util.Map;
import java.util.Optional;

public record CommandRequest(
        String command,
        Optional<String> subcommand,
        Map<String, String> options,
        DiscordId guildId,
        DiscordId channelId,
        DiscordId userId,
        String userDisplayName,
        boolean canManageServer,
        String locale) {
    public CommandRequest {
        options = Map.copyOf(options);
    }
}
