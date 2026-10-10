package com.minecraftdiscordbridge.core.config;

import com.minecraftdiscordbridge.core.discord.DiscordId;
import java.util.Optional;

public record DiscordConfig(
        Optional<DiscordId> chatChannel,
        Optional<DiscordId> eventsChannel,
        Optional<DiscordId> consoleChannel,
        boolean autoEnableMessageContent) {}
