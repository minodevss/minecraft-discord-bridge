package com.minecraftdiscordbridge.core.config;

import com.minecraftdiscordbridge.core.i18n.Language;
import java.util.List;

public record BridgeConfig(
        Language language,
        ChatConfig chat,
        EventsConfig events,
        PlayerCountConfig playerCount,
        LinkingConfig linking,
        ConsoleConfig console,
        DiscordCommandsConfig discordCommands,
        List<RoleSyncRule> roleSync) {
    public BridgeConfig {
        roleSync = List.copyOf(roleSync);
    }

    public static BridgeConfig defaults() {
        return ConfigSchema.toConfig(ConfigValues.defaults());
    }
}
