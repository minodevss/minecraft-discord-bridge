package com.minecraftdiscordbridge.core.access;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

final class AbsentGroups implements PermissionGroups {
    static final AbsentGroups INSTANCE = new AbsentGroups();
    private static final String ABSENT = "LuckPerms is not installed";

    private AbsentGroups() {}

    @Override
    public boolean present() {
        return false;
    }

    @Override
    public CompletableFuture<GroupCheck> inspect(String group) {
        return CompletableFuture.completedFuture(new GroupCheck.Missing());
    }

    @Override
    public CompletableFuture<PlayerGroups> groupsOf(UUID player) {
        return CompletableFuture.failedFuture(new IllegalStateException(ABSENT));
    }

    @Override
    public CompletableFuture<Void> addGroup(UUID player, String group) {
        return CompletableFuture.failedFuture(new IllegalStateException(ABSENT));
    }

    @Override
    public CompletableFuture<Void> removeGroup(UUID player, String group) {
        return CompletableFuture.failedFuture(new IllegalStateException(ABSENT));
    }

    @Override
    public CompletableFuture<Boolean> hasPermission(UUID player, String node) {
        return CompletableFuture.completedFuture(false);
    }

    @Override
    public Runnable onGroupsChanged(Consumer<UUID> changed) {
        return () -> {};
    }
}
