package com.minecraftdiscordbridge.core.discord.jda;

import static com.minecraftdiscordbridge.core.log.Log.LOGGER;

import com.minecraftdiscordbridge.core.discord.Author;
import com.minecraftdiscordbridge.core.discord.DiscordId;
import com.minecraftdiscordbridge.core.discord.DiscordStatus.ChannelStatus;
import com.minecraftdiscordbridge.core.discord.OutboundMessage;
import com.minecraftdiscordbridge.core.log.Failures;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import org.jspecify.annotations.Nullable;

final class Outbox {
    static final int CAPACITY = 200;

    private final Author server;
    private final MessageSender sender;
    private final Executor serial;
    private final Map<DiscordId, Channel> channels = new HashMap<>();
    private volatile Map<DiscordId, ChannelStatus> snapshot = Map.of();
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

    Map<DiscordId, ChannelStatus> snapshot() {
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

    private void drain(DiscordId channelId, Channel channel) {
        if (closed || !open || channel.inFlight) {
            return;
        }
        Optional<Batches.Batch> next = Batches.take(channelId, channel.queue, channel.skipped, server);
        if (next.isEmpty()) {
            return;
        }
        channel.skipped = 0;
        channel.inFlight = true;
        Batches.Batch batch = next.get();
        var unused = start(batch.message())
                .whenComplete((ignored, failure) -> serial.execute(() -> finish(channelId, channel, batch, failure)));
    }

    private void finish(DiscordId channelId, Channel channel, Batches.Batch batch, @Nullable Throwable failure) {
        channel.inFlight = false;
        if (failure == null) {
            if (channel.failing) {
                LOGGER.info("Sending to Discord channel {} works again", channelId);
            }
            channel.failing = false;
        } else {
            channel.dropped += batch.messages();
            if (!channel.failing) {
                LOGGER.warn(
                        "Could not send to Discord channel {}: {}. Check that the bot can see the channel and post in it",
                        channelId,
                        Failures.describe(failure));
            }
            channel.failing = true;
        }
        drain(channelId, channel);
        publish();
    }

    private CompletionStage<Void> start(OutboundMessage message) {
        try {
            return sender.send(message);
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    private void publish() {
        Map<DiscordId, ChannelStatus> next = new HashMap<>();
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
