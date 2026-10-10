package com.minecraftdiscordbridge.core.discord;

import java.util.Set;

record Post(Author author, String content, Set<Long> pingUserIds) {
    Post {
        pingUserIds = Set.copyOf(pingUserIds);
    }

    static Post of(OutboundMessage message) {
        return new Post(
                message.author(),
                DiscordText.fit(message.text(), DiscordText.MAX_MESSAGE_LENGTH),
                message.pingUserIds());
    }

    String contentAsBot() {
        return author.isServer() ? content : DiscordText.speakerLine(author.name(), content);
    }
}
