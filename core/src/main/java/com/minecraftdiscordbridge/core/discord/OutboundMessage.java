package com.minecraftdiscordbridge.core.discord;

import java.util.Set;

public record OutboundMessage(
        DiscordId channelId, Author author, String text, Set<DiscordId> pingUserIds, Set<DiscordId> pingRoleIds) {
    public static final int MAX_LENGTH = 2000;

    public OutboundMessage {
        pingUserIds = Set.copyOf(pingUserIds);
        pingRoleIds = Set.copyOf(pingRoleIds);
    }

    public OutboundMessage(DiscordId channelId, Author author, String text, Set<DiscordId> pingUserIds) {
        this(channelId, author, text, pingUserIds, Set.of());
    }

    public static OutboundMessage of(DiscordId channelId, Author author, String text) {
        return new OutboundMessage(channelId, author, text, Set.of(), Set.of());
    }
}
