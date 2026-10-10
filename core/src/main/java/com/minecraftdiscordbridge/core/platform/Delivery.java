package com.minecraftdiscordbridge.core.platform;

import java.util.Set;
import java.util.UUID;

public record Delivery(String json, Set<UUID> excluded) {
    public Delivery {
        excluded = Set.copyOf(excluded);
    }
}
