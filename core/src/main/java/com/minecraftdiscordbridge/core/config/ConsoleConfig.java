package com.minecraftdiscordbridge.core.config;

import com.minecraftdiscordbridge.core.discord.DiscordId;
import java.util.List;
import java.util.Optional;

public record ConsoleConfig(Optional<DiscordId> channel, List<DiscordId> inputUsers) {
    public ConsoleConfig {
        inputUsers = List.copyOf(inputUsers);
    }
}
