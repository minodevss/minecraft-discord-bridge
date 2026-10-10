package com.minecraftdiscordbridge.core.storage;

import java.util.Map;
import java.util.Optional;

public record ChannelBinding(String guildId, Map<ChannelRole, String> channels) {
    public ChannelBinding {
        DiscordIds.require(guildId, "guildId");
        channels.forEach((role, channel) -> DiscordIds.require(channel, role.id() + " channel"));
        channels = Map.copyOf(channels);
    }

    public Optional<String> channel(ChannelRole role) {
        return Optional.ofNullable(channels.get(role));
    }
}
