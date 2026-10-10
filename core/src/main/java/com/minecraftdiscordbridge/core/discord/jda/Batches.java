package com.minecraftdiscordbridge.core.discord.jda;

import com.minecraftdiscordbridge.core.discord.Author;
import com.minecraftdiscordbridge.core.discord.DiscordId;
import com.minecraftdiscordbridge.core.discord.OutboundMessage;
import com.minecraftdiscordbridge.core.text.Graphemes;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

final class Batches {
    static final int CROWD_SIZE = 5;
    static final int MAX_PINGS = 100;

    private Batches() {}

    static Optional<Batch> take(DiscordId channelId, Deque<OutboundMessage> queue, long skipped, Author server) {
        if (skipped > 0 || isCrowded(queue)) {
            return Optional.of(digest(channelId, queue, skipped, server));
        }
        if (queue.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(sameAuthorRun(queue));
    }

    private static boolean isCrowded(Deque<OutboundMessage> queue) {
        return queue.size() >= CROWD_SIZE
                && queue.stream()
                                .map(message -> message.author().key())
                                .distinct()
                                .count()
                        > 1;
    }

    private static Batch sameAuthorRun(Deque<OutboundMessage> queue) {
        OutboundMessage first = queue.removeFirst();
        Lines lines = new Lines();
        lines.addMessage(first);
        OutboundMessage next = queue.peekFirst();
        while (next != null && next.author().key().equals(first.author().key()) && lines.fits(next.text())) {
            queue.removeFirst();
            lines.addMessage(next);
            next = queue.peekFirst();
        }
        return lines.batch(first.channelId(), first.author());
    }

    private static Batch digest(DiscordId channelId, Deque<OutboundMessage> queue, long skipped, Author server) {
        Lines lines = new Lines();
        if (skipped > 0) {
            lines.add(skipped == 1 ? "(1 message skipped)" : "(" + skipped + " messages skipped)");
        }
        OutboundMessage next = queue.peekFirst();
        while (next != null && (lines.isEmpty() || lines.fits(DiscordText.asBot(next)))) {
            queue.removeFirst();
            lines.addMessage(DiscordText.asBot(next), next);
            next = queue.peekFirst();
        }
        return lines.batch(channelId, server);
    }

    record Batch(OutboundMessage message, int messages) {}

    private static final class Lines {
        private final StringBuilder content = new StringBuilder();
        private final Set<DiscordId> pings = new LinkedHashSet<>();
        private final Set<DiscordId> rolePings = new LinkedHashSet<>();
        private int messages;

        boolean isEmpty() {
            return content.length() == 0;
        }

        boolean fits(String line) {
            return content.length() + 1 + line.length() <= OutboundMessage.MAX_LENGTH;
        }

        void addMessage(OutboundMessage message) {
            addMessage(message.text(), message);
        }

        void addMessage(String line, OutboundMessage message) {
            add(line);
            collect(message.pingUserIds(), pings);
            collect(message.pingRoleIds(), rolePings);
            messages++;
        }

        void add(String line) {
            if (!isEmpty()) {
                content.append('\n');
            }
            content.append(Graphemes.fit(line, OutboundMessage.MAX_LENGTH - content.length(), Graphemes.ELLIPSIS));
        }

        private static void collect(Set<DiscordId> from, Set<DiscordId> into) {
            for (DiscordId ping : from) {
                if (into.size() < MAX_PINGS) {
                    into.add(ping);
                }
            }
        }

        Batch batch(DiscordId channelId, Author author) {
            return new Batch(new OutboundMessage(channelId, author, content.toString(), pings, rolePings), messages);
        }
    }
}
