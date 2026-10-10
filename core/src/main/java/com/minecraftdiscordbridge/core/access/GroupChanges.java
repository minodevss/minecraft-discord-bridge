package com.minecraftdiscordbridge.core.access;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

final class GroupChanges {
    private final Map<UUID, Set<String>> known = new ConcurrentHashMap<>();
    private final Consumer<UUID> changed;

    GroupChanges(Consumer<UUID> changed) {
        this.changed = changed;
    }

    void recalculated(UUID player, Set<String> groups) {
        Set<String> previous = known.put(player, Set.copyOf(groups));
        if (previous != null && !previous.equals(groups)) {
            changed.accept(player);
        }
    }

    void edited(UUID player) {
        changed.accept(player);
    }

    void unloaded(UUID player) {
        known.remove(player);
    }
}
