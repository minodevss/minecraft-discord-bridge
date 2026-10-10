package com.minecraftdiscordbridge.core.config;

import com.minecraftdiscordbridge.core.discord.DiscordId;
import java.util.Optional;

public record EventsConfig(
        Optional<DiscordId> channel,
        boolean join,
        boolean leave,
        boolean death,
        boolean advancement,
        boolean serverStart,
        boolean serverStop,
        Optional<DiscordId> serverStartPingRole) {}
