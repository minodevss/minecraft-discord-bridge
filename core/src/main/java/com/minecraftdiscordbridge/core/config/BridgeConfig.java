package com.minecraftdiscordbridge.core.config;

import com.minecraftdiscordbridge.core.i18n.Language;

public record BridgeConfig(
        Language language,
        DiscordConfig discord,
        ChatConfig chat,
        EventsConfig events,
        StatusConfig status,
        LinkingConfig linking,
        RoleSyncConfig roleSync,
        ActionsConfig actions,
        ConsoleConfig console) {
    public static BridgeConfig defaults() {
        return ConfigSchema.toConfig(ConfigValues.defaults());
    }
}
