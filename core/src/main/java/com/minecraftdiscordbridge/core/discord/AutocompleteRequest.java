package com.minecraftdiscordbridge.core.discord;

public record AutocompleteRequest(
        String command, String option, String typed, long guildId, long channelId, long userId, String locale) {}
