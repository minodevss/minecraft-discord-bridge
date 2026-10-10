package com.minecraftdiscordbridge.core.config;

import java.util.Optional;

public record DiscordConfig(
        Optional<String> chatChannel,
        Optional<String> eventsChannel,
        Optional<String> consoleChannel,
        boolean autoEnableMessageContent) {}
