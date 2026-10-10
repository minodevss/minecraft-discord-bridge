package com.minecraftdiscordbridge.core.config;

import java.util.List;

public record ConsoleConfig(boolean mirror, List<String> inputUsers) {
    public ConsoleConfig {
        inputUsers = List.copyOf(inputUsers);
    }
}
