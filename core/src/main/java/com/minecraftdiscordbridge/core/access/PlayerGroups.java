package com.minecraftdiscordbridge.core.access;

import java.util.Set;

public record PlayerGroups(Set<String> direct, Set<String> inherited) {
    public PlayerGroups {
        direct = Set.copyOf(direct);
        inherited = Set.copyOf(inherited);
    }
}
