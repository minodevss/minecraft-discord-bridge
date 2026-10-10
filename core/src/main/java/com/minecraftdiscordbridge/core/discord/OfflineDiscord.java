package com.minecraftdiscordbridge.core.discord;

import com.minecraftdiscordbridge.core.loop.Deadline;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class OfflineDiscord implements Discord {
    public static final String STARTING = "The bridge is starting";
    public static final String CONNECTING = "Connecting to Discord";
    public static final String NO_TOKEN = "No bot token is set. See the server log for the setup steps";
    public static final String CONFIG_ERRORS =
            "The settings have errors. See the server log, fix them and run /discord reload";
    public static final String STOPPED = "The bridge has stopped";

    private final DiscordStatus.State state;
    private final String reason;

    private OfflineDiscord(DiscordStatus.State state, String reason) {
        this.state = state;
        this.reason = reason;
    }

    public static OfflineDiscord stopped(String reason) {
        return new OfflineDiscord(DiscordStatus.State.STOPPED, reason);
    }

    public static OfflineDiscord connecting(String reason) {
        return new OfflineDiscord(DiscordStatus.State.CONNECTING, reason);
    }

    @Override
    public void start() {}

    @Override
    public DiscordStatus status() {
        return new DiscordStatus(state, Optional.of(reason), -1, Map.of());
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
        return CompletableFuture.completedFuture(new MemberLookup.Unavailable(reason));
    }

    @Override
    public Map<DiscordId, RoleInfo> roles() {
        return Map.of();
    }

    @Override
    public CompletableFuture<Void> addRole(DiscordId userId, DiscordId roleId) {
        return CompletableFuture.failedFuture(new IllegalStateException(reason));
    }

    @Override
    public CompletableFuture<Void> removeRole(DiscordId userId, DiscordId roleId) {
        return CompletableFuture.failedFuture(new IllegalStateException(reason));
    }

    @Override
    public void shutdown(List<OutboundMessage> finalMessages, Deadline deadline) {}
}
