package com.minecraftdiscordbridge.core.status;

import com.minecraftdiscordbridge.core.platform.PlayerProfile;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class Roster {
    private final Map<UUID, PlayerProfile> online = new LinkedHashMap<>();
    private final Set<UUID> vanished = new HashSet<>();

    public void reset(List<PlayerProfile> players) {
        online.clear();
        vanished.clear();
        players.forEach(player -> online.put(player.id(), player));
    }

    public void joined(PlayerProfile player, boolean hidden) {
        online.put(player.id(), player);
        if (hidden) {
            vanished.add(player.id());
        } else {
            vanished.remove(player.id());
        }
    }

    public void left(PlayerProfile player) {
        online.remove(player.id());
        vanished.remove(player.id());
    }

    public Optional<PlayerProfile> find(UUID id) {
        return Optional.ofNullable(online.get(id));
    }

    public int visibleCount() {
        return online.size() - vanished.size();
    }

    public List<String> visibleNames() {
        return online.values().stream()
                .filter(player -> !vanished.contains(player.id()))
                .map(PlayerProfile::name)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }
}
