package com.minecraftdiscordbridge.core;

import com.minecraftdiscordbridge.core.discord.CommandHashRepository;
import com.minecraftdiscordbridge.core.discord.Discord;
import com.minecraftdiscordbridge.core.discord.DiscordListener;
import com.minecraftdiscordbridge.core.discord.DiscordSettings;
import com.minecraftdiscordbridge.core.discord.WebhookRepository;
import com.minecraftdiscordbridge.core.loop.CoreLoop;

@FunctionalInterface
interface DiscordFactory {
    Discord create(
            DiscordSettings settings,
            CoreLoop loop,
            DiscordListener listener,
            WebhookRepository webhooks,
            CommandHashRepository commandHashes);
}
