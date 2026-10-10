package com.minecraftdiscordbridge.core.discord;

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
    public void updateTopic(long channelId, String topic) {
        target.updateTopic(channelId, topic);
    }

    @Override
    public boolean isApplicationOwner(long userId) {
        return target.isApplicationOwner(userId);
    }

    @Override
    public CompletableFuture<MemberLookup> member(long userId) {
        return target.member(userId);
    }

    @Override
    public Map<Long, RoleInfo> roles() {
        return target.roles();
    }

    @Override
    public CompletableFuture<Void> addRole(long userId, long roleId) {
        return target.addRole(userId, roleId);
    }

    @Override
    public CompletableFuture<Void> removeRole(long userId, long roleId) {
        return target.removeRole(userId, roleId);
    }

    @Override
    public void shutdown(List<OutboundMessage> finalMessages) {
        target.shutdown(finalMessages);
    }
}
