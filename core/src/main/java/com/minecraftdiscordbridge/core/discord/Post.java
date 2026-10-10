package com.minecraftdiscordbridge.core.discord;

import java.util.Set;

record Post(Author author, String content, Set<Long> pingUserIds, Set<Long> pingRoleIds) {
    Post {
        pingUserIds = Set.copyOf(pingUserIds);
        pingRoleIds = Set.copyOf(pingRoleIds);
    }

    Post(Author author, String content, Set<Long> pingUserIds) {
        this(author, content, pingUserIds, Set.of());
    }

    static Post of(OutboundMessage message) {
        return new Post(
                message.author(),
                DiscordText.fit(message.text(), DiscordText.MAX_MESSAGE_LENGTH),
                message.pingUserIds(),
                message.pingRoleIds());
    }

    String contentAsBot() {
        return author.isServer() ? content : DiscordText.speakerLine(author.name(), content);
    }
}
