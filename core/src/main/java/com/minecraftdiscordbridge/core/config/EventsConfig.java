package com.minecraftdiscordbridge.core.config;

import java.util.Optional;

public record EventsConfig(
        boolean join,
        boolean leave,
        boolean death,
        boolean advancement,
        boolean serverStart,
        boolean serverStop,
        Optional<String> serverStartPingRole) {}
