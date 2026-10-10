package com.minecraftdiscordbridge.core;

import com.minecraftdiscordbridge.core.chat.DiscordChat;
import com.minecraftdiscordbridge.core.chat.MinecraftChat;
import com.minecraftdiscordbridge.core.commands.GameCommands;
import com.minecraftdiscordbridge.core.commands.SlashRouter;
import com.minecraftdiscordbridge.core.events.EventPosts;
import com.minecraftdiscordbridge.core.i18n.Replies;
import com.minecraftdiscordbridge.core.status.StatusBoard;

record Session(
        Replies replies,
        MinecraftChat minecraftChat,
        DiscordChat discordChat,
        EventPosts events,
        StatusBoard status,
        SlashRouter slash,
        GameCommands commands) {}
