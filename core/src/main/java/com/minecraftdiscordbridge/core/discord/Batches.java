package com.minecraftdiscordbridge.core.discord;

import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

final class Batches {
    static final int CROWD_SIZE = 5;
    static final int MAX_PINGS = 100;

    private Batches() {}

    static Optional<Batch> take(Deque<OutboundMessage> queue, long skipped, Author server) {
        if (skipped > 0 || isCrowded(queue)) {
            return Optional.of(digest(queue, skipped, server));
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
        return lines.post(first.author());
    }

    private static Batch digest(Deque<OutboundMessage> queue, long skipped, Author server) {
        Lines lines = new Lines();
        if (skipped > 0) {
            lines.add(skipped == 1 ? "(1 message skipped)" : "(" + skipped + " messages skipped)");
        }
        OutboundMessage next = queue.peekFirst();
        while (next != null && (lines.isEmpty() || lines.fits(digestLine(next)))) {
            queue.removeFirst();
            lines.addMessage(digestLine(next), next);
            next = queue.peekFirst();
        }
        return lines.post(server);
    }

    private static String digestLine(OutboundMessage message) {
        return message.author().isServer()
                ? message.text()
                : DiscordText.speakerLine(message.author().name(), message.text());
    }

    record Batch(Post post, int messages) {}

    private static final class Lines {
        private final StringBuilder content = new StringBuilder();
        private final Set<Long> pings = new LinkedHashSet<>();
        private final Set<Long> rolePings = new LinkedHashSet<>();
        private int messages;

        boolean isEmpty() {
            return content.length() == 0;
        }

        boolean fits(String line) {
            return content.length() + 1 + line.length() <= DiscordText.MAX_MESSAGE_LENGTH;
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
            content.append(DiscordText.fit(line, DiscordText.MAX_MESSAGE_LENGTH - content.length()));
        }

        private static void collect(Set<Long> from, Set<Long> into) {
            for (Long ping : from) {
                if (into.size() < MAX_PINGS) {
                    into.add(ping);
                }
            }
        }

        Batch post(Author author) {
            return new Batch(new Post(author, content.toString(), pings, rolePings), messages);
        }
    }
}
