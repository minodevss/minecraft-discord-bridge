package com.minecraftdiscordbridge.core.discord;

import java.util.List;
import java.util.Set;

public record DiscordSettings(
        String token,
        long guildId,
        Set<Long> channelIds,
        Author serverAuthor,
        boolean autoEnableMessageContent,
        boolean requestMembersIntent,
        boolean relayBotMessages,
        boolean relayWebhookMessages,
        List<CommandSpec> commands) {
    public static final long NO_GUILD = 0;

    public DiscordSettings {
        channelIds = Set.copyOf(channelIds);
        commands = List.copyOf(commands);
    }

    @Override
    public String toString() {
        return "DiscordSettings[guildId=" + guildId + ", channelIds=" + channelIds + ", token=<hidden>]";
    }
}
