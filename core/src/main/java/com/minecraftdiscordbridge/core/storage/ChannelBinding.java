package com.minecraftdiscordbridge.core.storage;

import com.minecraftdiscordbridge.core.discord.DiscordId;
import java.util.Map;
import java.util.Optional;

public record ChannelBinding(DiscordId guildId, Map<ChannelRole, DiscordId> channels) {
    public ChannelBinding {
        channels = Map.copyOf(channels);
    }

    public Optional<DiscordId> channel(ChannelRole role) {
        return Optional.ofNullable(channels.get(role));
    }
}
