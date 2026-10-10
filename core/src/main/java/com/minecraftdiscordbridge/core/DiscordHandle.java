package com.minecraftdiscordbridge.core;

import com.minecraftdiscordbridge.core.discord.Discord;
import com.minecraftdiscordbridge.core.discord.DiscordStatus;
import com.minecraftdiscordbridge.core.discord.OutboundMessage;
import java.util.List;

final class DiscordHandle implements Discord {
    private volatile Discord target;

    DiscordHandle(Discord initial) {
        this.target = initial;
    }

    synchronized Discord swap(Discord next) {
        Discord previous = target;
        target = next;
        return previous;
    }

    @Override
    public void start() {
        target.start();
    }

    @Override
    public DiscordStatus status() {
        return target.status();
    }

    @Override
    public void send(OutboundMessage message) {
        target.send(message);
    }

    @Override
    public void updatePresence(String text) {
        target.updatePresence(text);
    }

    @Override
    public void updateTopic(long channelId, String topic) {
        target.updateTopic(channelId, topic);
    }

    @Override
    public boolean isApplicationOwner(long userId) {
        return target.isApplicationOwner(userId);
    }

    @Override
    public void shutdown(List<OutboundMessage> finalMessages) {
        target.shutdown(finalMessages);
    }
}
