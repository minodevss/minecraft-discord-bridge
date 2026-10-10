package com.minecraftdiscordbridge.core.config;

import com.minecraftdiscordbridge.core.discord.DiscordId;
import java.util.List;

public record ConsoleConfig(boolean mirror, List<DiscordId> inputUsers) {
    public ConsoleConfig {
        inputUsers = List.copyOf(inputUsers);
    }
}
