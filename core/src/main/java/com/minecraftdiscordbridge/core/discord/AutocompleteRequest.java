package com.minecraftdiscordbridge.core.discord;

public record AutocompleteRequest(
        String command,
        String option,
        String typed,
        DiscordId guildId,
        DiscordId channelId,
        DiscordId userId,
        String locale) {}
