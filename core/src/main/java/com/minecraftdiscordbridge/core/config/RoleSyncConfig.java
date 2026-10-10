package com.minecraftdiscordbridge.core.config;

import java.util.List;

public record RoleSyncConfig(List<RoleSyncRule> rules) {
    public RoleSyncConfig {
        rules = List.copyOf(rules);
    }
}
