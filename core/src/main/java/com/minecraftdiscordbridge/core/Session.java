package com.minecraftdiscordbridge.core;

import com.minecraftdiscordbridge.core.access.JoinGate;
import com.minecraftdiscordbridge.core.access.RoleSync;
import com.minecraftdiscordbridge.core.chat.DiscordChat;
import com.minecraftdiscordbridge.core.chat.MinecraftChat;
import com.minecraftdiscordbridge.core.commands.GameCommands;
import com.minecraftdiscordbridge.core.commands.SlashRouter;
import com.minecraftdiscordbridge.core.console.ConsoleInput;
import com.minecraftdiscordbridge.core.console.ConsoleMirror;
import com.minecraftdiscordbridge.core.events.EventPosts;
import com.minecraftdiscordbridge.core.i18n.Replies;
import com.minecraftdiscordbridge.core.status.StatusBoard;
import java.util.Optional;

record Session(
        Replies replies,
        MinecraftChat minecraftChat,
        DiscordChat discordChat,
        EventPosts events,
        StatusBoard status,
        SlashRouter slash,
        GameCommands commands,
        Optional<ConsoleMirror> console,
        JoinGate gate,
        RoleSync roleSync,
        ConsoleInput consoleInput) {
    void close() {
        console.ifPresent(ConsoleMirror::close);
    }
}
