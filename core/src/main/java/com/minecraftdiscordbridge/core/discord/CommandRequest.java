package com.minecraftdiscordbridge.core.discord;

import java.util.Map;
import org.jspecify.annotations.Nullable;

public record CommandRequest(
        String command,
        @Nullable String subcommand,
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
