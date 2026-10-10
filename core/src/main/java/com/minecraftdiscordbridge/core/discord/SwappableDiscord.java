package com.minecraftdiscordbridge.core.discord;

import com.minecraftdiscordbridge.core.loop.Deadline;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class SwappableDiscord implements Discord {
    private volatile Discord target;

    public SwappableDiscord(Discord initial) {
        this.target = initial;
    }

    public synchronized Discord swap(Discord next) {
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
    public void updateTopic(DiscordId channelId, String topic) {
        target.updateTopic(channelId, topic);
    }

    @Override
    public boolean isApplicationOwner(DiscordId userId) {
        return target.isApplicationOwner(userId);
    }

    @Override
    public CompletableFuture<MemberLookup> member(DiscordId userId) {
        return target.member(userId);
    }

    @Override
    public Map<DiscordId, RoleInfo> roles() {
        return target.roles();
    }

    @Override
    public CompletableFuture<Void> addRole(DiscordId userId, DiscordId roleId) {
        return target.addRole(userId, roleId);
    }

    @Override
    public CompletableFuture<Void> removeRole(DiscordId userId, DiscordId roleId) {
        return target.removeRole(userId, roleId);
    }

    @Override
    public void shutdown(List<OutboundMessage> finalMessages, Deadline deadline) {
        target.shutdown(finalMessages, deadline);
    }
}
