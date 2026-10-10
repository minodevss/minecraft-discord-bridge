package com.minecraftdiscordbridge.core;

import com.minecraftdiscordbridge.core.discord.AutocompleteReply;
import com.minecraftdiscordbridge.core.discord.AutocompleteRequest;
import com.minecraftdiscordbridge.core.discord.Discord;
import com.minecraftdiscordbridge.core.discord.DiscordListener;
import com.minecraftdiscordbridge.core.discord.DiscordMessage;
import com.minecraftdiscordbridge.core.discord.DiscordSettings;
import com.minecraftdiscordbridge.core.discord.DiscordStatus;
import com.minecraftdiscordbridge.core.discord.InteractionRequest;
import com.minecraftdiscordbridge.core.discord.OutboundMessage;
import com.minecraftdiscordbridge.core.loop.CoreLoop;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;

public final class RecordingDiscord implements Discord {
    public final DiscordSettings settings;
    public final BlockingQueue<OutboundMessage> sent = new LinkedBlockingQueue<>();
    public final List<String> presences = new CopyOnWriteArrayList<>();
    public final List<String> topics = new CopyOnWriteArrayList<>();
    public final List<OutboundMessage> finalMessages = new CopyOnWriteArrayList<>();
    public volatile Set<Long> owners = Set.of();
    public volatile Duration shutdownTime = Duration.ZERO;
    public volatile boolean started;
    public volatile boolean shutDown;
    private final CoreLoop loop;
    private final DiscordListener listener;

    public RecordingDiscord(DiscordSettings settings, CoreLoop loop, DiscordListener listener) {
        this.settings = settings;
        this.loop = loop;
        this.listener = listener;
    }

    public void receive(DiscordMessage message) {
        loop.content(() -> listener.onMessage(message));
    }

    public RecordingReply command(InteractionRequest request) {
        RecordingReply reply = new RecordingReply();
        loop.control(() -> listener.onCommand(request, reply));
        return reply;
    }

    public void autocomplete(AutocompleteRequest request, AutocompleteReply reply) {
        loop.control(() -> listener.onAutocomplete(request, reply));
    }

    public OutboundMessage nextSent() {
        return Waits.take(sent);
    }

    @Override
    public void start() {
        started = true;
    }

    @Override
    public DiscordStatus status() {
        return new DiscordStatus(
                shutDown ? DiscordStatus.State.STOPPED : DiscordStatus.State.READY, null, 42, Map.of());
    }

    @Override
    public void send(OutboundMessage message) {
        sent.add(message);
    }

    @Override
    public void updatePresence(String text) {
        presences.add(text);
    }

    @Override
    public void updateTopic(long channelId, String topic) {
        topics.add(channelId + ":" + topic);
    }

    @Override
    public boolean isApplicationOwner(long userId) {
        return owners.contains(userId);
    }

    @Override
    public void shutdown(List<OutboundMessage> finals) {
        finalMessages.addAll(finals);
        try {
            Thread.sleep(shutdownTime.toMillis());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        shutDown = true;
    }
}
