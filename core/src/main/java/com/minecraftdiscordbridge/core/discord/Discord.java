package com.minecraftdiscordbridge.core.discord;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface Discord {
    String NOT_CONNECTED = "Discord is not connected";

    void start();

    DiscordStatus status();

    void send(OutboundMessage message);

    void updatePresence(String text);

    void updateTopic(long channelId, String topic);

    boolean isApplicationOwner(long userId);

    default CompletableFuture<MemberLookup> member(long userId) {
        return CompletableFuture.completedFuture(new MemberLookup.Unavailable(NOT_CONNECTED));
    }

    default Map<Long, RoleInfo> roles() {
        return Map.of();
    }

    default CompletableFuture<Void> addRole(long userId, long roleId) {
        return CompletableFuture.failedFuture(new IllegalStateException(NOT_CONNECTED));
    }

    default CompletableFuture<Void> removeRole(long userId, long roleId) {
        return CompletableFuture.failedFuture(new IllegalStateException(NOT_CONNECTED));
    }

    void shutdown(List<OutboundMessage> finalMessages);
}
