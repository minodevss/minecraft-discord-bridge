package com.minecraftdiscordbridge.core;

import com.minecraftdiscordbridge.core.discord.Discord;
import com.minecraftdiscordbridge.core.discord.DiscordStatus;
import com.minecraftdiscordbridge.core.discord.MemberLookup;
import com.minecraftdiscordbridge.core.discord.OutboundMessage;
import com.minecraftdiscordbridge.core.discord.RoleInfo;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

final class OfflineDiscord implements Discord {
    static final String STARTING = "The bridge is starting";
    static final String CONNECTING = "Connecting to Discord";
    static final String NO_TOKEN = "No bot token is set. See the server log for the setup steps";
    static final String CONFIG_ERRORS =
            "The settings have errors. See the server log, fix them and run /discord reload";
    static final String STOPPED = "The bridge has stopped";

    private final DiscordStatus status;
    private final String reason;

    OfflineDiscord(DiscordStatus.State state, String reason) {
        this.status = new DiscordStatus(state, reason, -1, Map.of());
        this.reason = reason;
    }

    static OfflineDiscord stopped(String reason) {
        return new OfflineDiscord(DiscordStatus.State.STOPPED, reason);
    }

    static OfflineDiscord connecting(String reason) {
        return new OfflineDiscord(DiscordStatus.State.CONNECTING, reason);
    }

    @Override
    public void start() {}

    @Override
    public DiscordStatus status() {
        return status;
    }

    @Override
    public void send(OutboundMessage message) {}

    @Override
    public void updatePresence(String text) {}

    @Override
    public void updateTopic(long channelId, String topic) {}

    @Override
    public boolean isApplicationOwner(long userId) {
        return false;
    }

    @Override
    public CompletableFuture<MemberLookup> member(long userId) {
        return CompletableFuture.completedFuture(new MemberLookup.Unavailable(reason));
    }

    @Override
    public Map<Long, RoleInfo> roles() {
        return Map.of();
    }

    @Override
    public CompletableFuture<Void> addRole(long userId, long roleId) {
        return CompletableFuture.failedFuture(new IllegalStateException(reason));
    }

    @Override
    public CompletableFuture<Void> removeRole(long userId, long roleId) {
        return CompletableFuture.failedFuture(new IllegalStateException(reason));
    }

    @Override
    public void shutdown(List<OutboundMessage> finalMessages) {}
}
