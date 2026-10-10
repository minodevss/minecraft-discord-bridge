package com.minecraftdiscordbridge.core.discord;

import com.minecraftdiscordbridge.core.loop.Deadline;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface Discord {
    void start();

    DiscordStatus status();

    void send(OutboundMessage message);

    void updatePresence(String text);

    void updateTopic(DiscordId channelId, String topic);

    boolean isApplicationOwner(DiscordId userId);

    CompletableFuture<MemberLookup> member(DiscordId userId);

    Map<DiscordId, RoleInfo> roles();

    CompletableFuture<Void> addRole(DiscordId userId, DiscordId roleId);

    CompletableFuture<Void> removeRole(DiscordId userId, DiscordId roleId);

    void shutdown(List<OutboundMessage> finalMessages, Deadline deadline);
}
