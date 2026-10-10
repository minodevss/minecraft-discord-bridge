package com.minecraftdiscordbridge.core.access;

import com.minecraftdiscordbridge.core.discord.Discord;
import com.minecraftdiscordbridge.core.discord.DiscordStatus;
import com.minecraftdiscordbridge.core.discord.MemberLookup;
import com.minecraftdiscordbridge.core.discord.OutboundMessage;
import com.minecraftdiscordbridge.core.discord.RoleInfo;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.LongFunction;

final class StubDiscord implements Discord {
    final Map<Long, RoleInfo> roles = new ConcurrentHashMap<>();
    final List<Long> lookups = new CopyOnWriteArrayList<>();
    final List<String> roleChanges = new CopyOnWriteArrayList<>();
    volatile LongFunction<CompletableFuture<MemberLookup>> members =
            userId -> CompletableFuture.completedFuture(new MemberLookup.NotMember(userId));

    void memberWithRoles(long userId, Long... roleIds) {
        MemberLookup.Member member = new MemberLookup.Member(userId, Set.of(roleIds));
        members = id -> CompletableFuture.completedFuture(id == userId ? member : new MemberLookup.NotMember(id));
    }

    void unreachable() {
        members = id -> CompletableFuture.completedFuture(new MemberLookup.Unavailable("Discord is down"));
    }

    void role(long id, String name, String... dangerous) {
        roles.put(id, new RoleInfo(id, name, Set.of(dangerous), true));
    }

    @Override
    public void start() {}

    @Override
    public DiscordStatus status() {
        return new DiscordStatus(DiscordStatus.State.READY, null, 1, Map.of());
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
        lookups.add(userId);
        return members.apply(userId);
    }

    @Override
    public Map<Long, RoleInfo> roles() {
        return Map.copyOf(roles);
    }

    @Override
    public CompletableFuture<Void> addRole(long userId, long roleId) {
        roleChanges.add("+" + roleId);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> removeRole(long userId, long roleId) {
        roleChanges.add("-" + roleId);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void shutdown(List<OutboundMessage> finalMessages) {}
}
