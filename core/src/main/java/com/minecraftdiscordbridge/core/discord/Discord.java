package com.minecraftdiscordbridge.core.discord;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface Discord {
    void start();

    DiscordStatus status();

    void send(OutboundMessage message);

    void updatePresence(String text);

    void updateTopic(long channelId, String topic);

    boolean isApplicationOwner(long userId);

    CompletableFuture<MemberLookup> member(long userId);

    Map<Long, RoleInfo> roles();

    CompletableFuture<Void> addRole(long userId, long roleId);

    CompletableFuture<Void> removeRole(long userId, long roleId);

    void shutdown(List<OutboundMessage> finalMessages);
}
