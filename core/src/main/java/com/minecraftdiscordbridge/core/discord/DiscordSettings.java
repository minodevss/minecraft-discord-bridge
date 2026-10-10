package com.minecraftdiscordbridge.core.discord;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public record DiscordSettings(
        String token,
        Optional<DiscordId> guild,
        Set<DiscordId> channelIds,
        Author serverAuthor,
        boolean autoEnableMessageContent,
        boolean requestMembersIntent,
        List<CommandSpec> commands) {
    public DiscordSettings {
        channelIds = Set.copyOf(channelIds);
        commands = List.copyOf(commands);
    }

    @Override
    public String toString() {
        return "DiscordSettings[guild=" + guild.map(DiscordId::toString).orElse("none") + ", channelIds=" + channelIds
                + ", token=<hidden>]";
    }
}
