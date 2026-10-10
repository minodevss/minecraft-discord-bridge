package com.minecraftdiscordbridge.core.fakes;

import com.minecraftdiscordbridge.core.access.GroupCheck;
import com.minecraftdiscordbridge.core.access.PermissionGroups;
import com.minecraftdiscordbridge.core.access.PlayerGroups;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class FakeGroups implements PermissionGroups {
    public volatile boolean present = true;
    public final Map<String, GroupCheck> groups = new ConcurrentHashMap<>();
    public final Map<UUID, Set<String>> members = new ConcurrentHashMap<>();
    public final Map<UUID, Set<String>> permissions = new ConcurrentHashMap<>();
    public final List<String> changes = new CopyOnWriteArrayList<>();
    private final List<Consumer<UUID>> watchers = new CopyOnWriteArrayList<>();

    public void groupsChanged(UUID player) {
        watchers.forEach(watcher -> watcher.accept(player));
    }

    public int watchers() {
        return watchers.size();
    }

    @Override
    public boolean present() {
        return present;
    }

    @Override
    public CompletableFuture<GroupCheck> inspect(String group) {
        return CompletableFuture.completedFuture(groups.getOrDefault(group, new GroupCheck.Missing()));
    }

    @Override
    public CompletableFuture<PlayerGroups> groupsOf(UUID player) {
        Set<String> direct = members.getOrDefault(player, Set.of());
        return CompletableFuture.completedFuture(new PlayerGroups(direct, direct));
    }

    @Override
    public CompletableFuture<Void> addGroup(UUID player, String group) {
        changes.add("+" + group + " " + player);
        members.merge(player, Set.of(group), (old, added) -> union(old, added));
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> removeGroup(UUID player, String group) {
        changes.add("-" + group + " " + player);
        members.computeIfPresent(player, (id, old) -> without(old, group));
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Boolean> hasPermission(UUID player, String node) {
        return CompletableFuture.completedFuture(
                permissions.getOrDefault(player, Set.of()).contains(node));
    }

    @Override
    public Runnable onGroupsChanged(Consumer<UUID> changed) {
        watchers.add(changed);
        return () -> watchers.remove(changed);
    }

    private static Set<String> union(Set<String> first, Set<String> second) {
        Set<String> all = new HashSet<>(first);
        all.addAll(second);
        return Set.copyOf(all);
    }

    private static Set<String> without(Set<String> groups, String group) {
        Set<String> kept = new HashSet<>(groups);
        kept.remove(group);
        return Set.copyOf(kept);
    }
}
