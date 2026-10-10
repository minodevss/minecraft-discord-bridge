package com.minecraftdiscordbridge.core.discord.jda;

import com.minecraftdiscordbridge.core.discord.Author;
import com.minecraftdiscordbridge.core.discord.DiscordStatus.ChannelStatus;
import com.minecraftdiscordbridge.core.discord.OutboundMessage;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class Outbox {
    static final int CAPACITY = 200;
    private static final Logger LOGGER = LoggerFactory.getLogger("MinecraftDiscordBridge");

    private final Author server;
    private final MessageSender sender;
    private final Executor serial;
    private final Map<Long, Channel> channels = new HashMap<>();
    private volatile Map<Long, ChannelStatus> snapshot = Map.of();
    private volatile boolean closed;
    private boolean open;

    Outbox(Author server, MessageSender sender, Executor serial) {
        this.server = server;
        this.sender = sender;
        this.serial = serial;
    }

    void enqueue(OutboundMessage message) {
        if (closed || message.text().isBlank()) {
            return;
        }
        Channel channel = channels.computeIfAbsent(message.channelId(), id -> new Channel());
        channel.add(message);
        drain(message.channelId(), channel);
        publish();
    }

    Map<Long, ChannelStatus> snapshot() {
        return snapshot;
    }

    void open() {
        open = true;
        channels.forEach(this::drain);
        publish();
    }

    void hold() {
        open = false;
    }

    void close() {
        closed = true;
    }

    private void drain(long channelId, Channel channel) {
        if (closed || !open || channel.inFlight) {
            return;
        }
        Optional<Batches.Batch> next = Batches.take(channel.queue, channel.skipped, server);
        if (next.isEmpty()) {
            return;
        }
        channel.skipped = 0;
        channel.inFlight = true;
        Batches.Batch batch = next.get();
        var unused = start(channelId, batch.post())
                .whenComplete((ignored, failure) -> serial.execute(() -> finish(channelId, channel, batch, failure)));
    }

    private void finish(long channelId, Channel channel, Batches.Batch batch, @Nullable Throwable failure) {
        channel.inFlight = false;
        if (failure == null) {
            if (channel.failing) {
                LOGGER.info("Sending to Discord channel {} works again", Long.toUnsignedString(channelId));
            }
            channel.failing = false;
        } else {
            channel.dropped += batch.messages();
            if (!channel.failing) {
                LOGGER.warn(
                        "Could not send to Discord channel {}: {}. Check that the bot can see the channel and post in it",
                        Long.toUnsignedString(channelId),
                        Failures.describe(failure));
            }
            channel.failing = true;
        }
        drain(channelId, channel);
        publish();
    }

    private CompletionStage<Void> start(long channelId, Post post) {
        try {
            return sender.send(channelId, post);
        } catch (RuntimeException failure) {
            return Failures.failed(failure);
        }
    }

    private void publish() {
        Map<Long, ChannelStatus> next = new HashMap<>();
        channels.forEach((id, channel) -> next.put(id, channel.status()));
        snapshot = Map.copyOf(next);
    }

    private static final class Channel {
        private final ArrayDeque<OutboundMessage> queue = new ArrayDeque<>();
        private boolean inFlight;
        private long skipped;
        private long dropped;
        private boolean failing;

        void add(OutboundMessage message) {
            if (queue.size() >= CAPACITY) {
                queue.removeFirst();
                skipped++;
                dropped++;
            }
            queue.addLast(message);
        }

        ChannelStatus status() {
            return new ChannelStatus(queue.size(), inFlight ? 1 : 0, dropped, 0);
        }
    }
}
