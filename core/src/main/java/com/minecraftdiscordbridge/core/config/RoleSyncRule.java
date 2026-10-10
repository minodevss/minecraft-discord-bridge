package com.minecraftdiscordbridge.core.config;

import com.minecraftdiscordbridge.core.discord.DiscordId;

public record RoleSyncRule(DiscordId discordRole, String group, SyncDirection direction) {}
