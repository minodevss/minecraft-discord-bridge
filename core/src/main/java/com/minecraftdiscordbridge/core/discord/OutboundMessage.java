package com.minecraftdiscordbridge.core.discord;

import java.util.Set;

public record OutboundMessage(
        long channelId, Author author, String text, Set<Long> pingUserIds, Set<Long> pingRoleIds) {
    public static final int MAX_LENGTH = 2000;

    public OutboundMessage {
        pingUserIds = Set.copyOf(pingUserIds);
        pingRoleIds = Set.copyOf(pingRoleIds);
    }

    public OutboundMessage(long channelId, Author author, String text, Set<Long> pingUserIds) {
        this(channelId, author, text, pingUserIds, Set.of());
    }

    public static OutboundMessage of(long channelId, Author author, String text) {
        return new OutboundMessage(channelId, author, text, Set.of(), Set.of());
    }
}
