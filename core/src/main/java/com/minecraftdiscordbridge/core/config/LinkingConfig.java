package com.minecraftdiscordbridge.core.config;

import com.minecraftdiscordbridge.core.discord.DiscordId;
import java.util.Optional;

public record LinkingConfig(
        boolean enabled,
        boolean requireForJoin,
        Optional<DiscordId> requiredRole,
        UnreachablePolicy whenDiscordUnreachable) {
    public boolean gatesJoins() {
        return requireForJoin || requiredRole.isPresent();
    }
}
