package com.minecraftdiscordbridge.core.discord;

import java.util.Set;

public record RoleInfo(long id, String name, Set<String> dangerousPermissions, boolean assignable) {
    public RoleInfo {
        dangerousPermissions = Set.copyOf(dangerousPermissions);
    }

    public boolean dangerous() {
        return !dangerousPermissions.isEmpty();
    }
}
