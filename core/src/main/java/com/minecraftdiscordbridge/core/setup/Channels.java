package com.minecraftdiscordbridge.core.setup;

import com.minecraftdiscordbridge.core.config.DiscordConfig;
import com.minecraftdiscordbridge.core.discord.DiscordId;
import com.minecraftdiscordbridge.core.storage.ChannelBinding;
import com.minecraftdiscordbridge.core.storage.ChannelRole;
import java.util.Optional;

public record Channels(Optional<DiscordId> guild, Optional<DiscordId> chat, Optional<DiscordId> events) {
    public static Channels resolve(DiscordConfig config, Optional<ChannelBinding> setup) {
        Optional<DiscordId> chat = config.chatChannel().or(() -> fromSetup(setup, ChannelRole.CHAT));
        Optional<DiscordId> events = config.eventsChannel()
                .or(() -> fromSetup(setup, ChannelRole.EVENTS))
                .or(() -> chat);
        return new Channels(setup.map(ChannelBinding::guildId), chat, events);
    }

    private static Optional<DiscordId> fromSetup(Optional<ChannelBinding> setup, ChannelRole role) {
        return setup.flatMap(binding -> binding.channel(role));
    }
}
