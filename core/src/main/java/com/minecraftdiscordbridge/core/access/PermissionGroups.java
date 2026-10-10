package com.minecraftdiscordbridge.core.access;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public interface PermissionGroups {
    String LUCKPERMS_PROVIDER = "net.luckperms.api.LuckPermsProvider";

    boolean present();

    CompletableFuture<GroupCheck> inspect(String group);

    CompletableFuture<PlayerGroups> groupsOf(UUID player);

    CompletableFuture<Void> addGroup(UUID player, String group);

    CompletableFuture<Void> removeGroup(UUID player, String group);

    CompletableFuture<Boolean> hasPermission(UUID player, String node);

    Runnable onGroupsChanged(Consumer<UUID> changed);

    static PermissionGroups detect() {
        try {
            Class.forName(LUCKPERMS_PROVIDER, false, PermissionGroups.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError absent) {
            return AbsentGroups.INSTANCE;
        }
        return LuckPermsGroups.create();
    }
}
