package com.minecraftdiscordbridge.core.discord;

import com.minecraftdiscordbridge.core.loop.Deadline;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class OfflineDiscord implements Discord {
    public static final StatusReason STARTING = StatusReason.of(StatusReason.Kind.STARTING);
    public static final StatusReason CONNECTING = StatusReason.of(StatusReason.Kind.CONNECTING);
    public static final StatusReason NO_TOKEN = StatusReason.of(StatusReason.Kind.NO_TOKEN);
    public static final StatusReason CONFIG_ERRORS = StatusReason.of(StatusReason.Kind.CONFIG_ERRORS);
    public static final StatusReason STOPPED = StatusReason.of(StatusReason.Kind.STOPPED);
    private static final String NOT_CONNECTED = "Discord is not connected";

    private final DiscordStatus.State state;
    private final StatusReason reason;

    private OfflineDiscord(DiscordStatus.State state, StatusReason reason) {
        this.state = state;
        this.reason = reason;
    }

    public static OfflineDiscord stopped(StatusReason reason) {
        return new OfflineDiscord(DiscordStatus.State.STOPPED, reason);
    }

    public static OfflineDiscord connecting(StatusReason reason) {
        return new OfflineDiscord(DiscordStatus.State.CONNECTING, reason);
    }

    @Override
    public void start() {}

    @Override
    public DiscordStatus status() {
        return new DiscordStatus(state, List.of(reason), -1, Map.of());
    }

    @Override
    public void send(OutboundMessage message) {}

    @Override
    public void updatePresence(String text) {}

    @Override
    public void updateTopic(DiscordId channelId, String topic) {}

    @Override
    public boolean isApplicationOwner(DiscordId userId) {
        return false;
    }

    @Override
    public CompletableFuture<MemberLookup> member(DiscordId userId) {
        return CompletableFuture.completedFuture(new MemberLookup.Unavailable(NOT_CONNECTED));
    }

    @Override
    public Map<DiscordId, RoleInfo> roles() {
        return Map.of();
    }

    @Override
    public CompletableFuture<Void> addRole(DiscordId userId, DiscordId roleId) {
        return CompletableFuture.failedFuture(new IllegalStateException(NOT_CONNECTED));
    }

    @Override
    public CompletableFuture<Void> removeRole(DiscordId userId, DiscordId roleId) {
        return CompletableFuture.failedFuture(new IllegalStateException(NOT_CONNECTED));
    }

    @Override
    public void shutdown(List<OutboundMessage> finalMessages, Deadline deadline) {}
}
