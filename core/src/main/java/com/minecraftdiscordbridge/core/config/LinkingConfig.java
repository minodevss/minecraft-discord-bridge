package com.minecraftdiscordbridge.core.config;

import java.util.Optional;

public record LinkingConfig(
        boolean enabled,
        boolean requireForJoin,
        Optional<String> requiredRole,
        UnreachablePolicy whenDiscordUnreachable) {}
