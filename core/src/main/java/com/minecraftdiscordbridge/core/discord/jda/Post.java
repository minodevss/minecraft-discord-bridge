package com.minecraftdiscordbridge.core.discord.jda;

import com.minecraftdiscordbridge.core.discord.Author;
import com.minecraftdiscordbridge.core.discord.OutboundMessage;
import com.minecraftdiscordbridge.core.text.Graphemes;
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
                Graphemes.fit(message.text(), OutboundMessage.MAX_LENGTH, Graphemes.ELLIPSIS),
                message.pingUserIds(),
                message.pingRoleIds());
    }

    String contentAsBot() {
        return author.isServer() ? content : DiscordText.speakerLine(author.name(), content);
    }
}
