package com.minecraftdiscordbridge.core.config;

import com.minecraftdiscordbridge.core.storage.ChannelBinding;
import com.minecraftdiscordbridge.core.storage.ChannelRole;
import java.util.Optional;

public record Channels(
        Optional<String> guild, Optional<String> chat, Optional<String> events, Optional<String> console) {
    public static Channels resolve(DiscordConfig config, Optional<ChannelBinding> setup) {
        Optional<String> chat = config.chatChannel().or(() -> fromSetup(setup, ChannelRole.CHAT));
        Optional<String> events = config.eventsChannel()
                .or(() -> fromSetup(setup, ChannelRole.EVENTS))
                .or(() -> chat);
        Optional<String> console = config.consoleChannel().or(() -> fromSetup(setup, ChannelRole.CONSOLE));
        return new Channels(setup.map(ChannelBinding::guildId), chat, events, console);
    }

    private static Optional<String> fromSetup(Optional<ChannelBinding> setup, ChannelRole role) {
        return setup.flatMap(binding -> binding.channel(role));
    }
}
