package com.minecraftdiscordbridge.core.config;

import com.minecraftdiscordbridge.core.discord.DiscordId;
import com.minecraftdiscordbridge.core.storage.ChannelBinding;
import com.minecraftdiscordbridge.core.storage.ChannelRole;
import java.util.Optional;

public record Channels(
        Optional<DiscordId> guild, Optional<DiscordId> chat, Optional<DiscordId> events, Optional<DiscordId> console) {
    public static Channels resolve(DiscordConfig config, Optional<ChannelBinding> setup) {
        Optional<DiscordId> chat = config.chatChannel().or(() -> fromSetup(setup, ChannelRole.CHAT));
        Optional<DiscordId> events = config.eventsChannel()
                .or(() -> fromSetup(setup, ChannelRole.EVENTS))
                .or(() -> chat);
        Optional<DiscordId> console = config.consoleChannel().or(() -> fromSetup(setup, ChannelRole.CONSOLE));
        return new Channels(setup.map(ChannelBinding::guildId), chat, events, console);
    }

    private static Optional<DiscordId> fromSetup(Optional<ChannelBinding> setup, ChannelRole role) {
        return setup.flatMap(binding -> binding.channel(role));
    }
}
