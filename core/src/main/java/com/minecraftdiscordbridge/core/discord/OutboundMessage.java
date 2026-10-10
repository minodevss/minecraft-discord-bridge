package com.minecraftdiscordbridge.core.discord;

import java.util.Set;

public record OutboundMessage(long channelId, Author author, String text, Set<Long> pingUserIds) {
    public OutboundMessage {
        pingUserIds = Set.copyOf(pingUserIds);
    }

    public static OutboundMessage of(long channelId, Author author, String text) {
        return new OutboundMessage(channelId, author, text, Set.of());
    }
}
