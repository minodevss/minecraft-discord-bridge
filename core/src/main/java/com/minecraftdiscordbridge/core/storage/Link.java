package com.minecraftdiscordbridge.core.storage;

import java.time.Instant;
import java.util.UUID;

public record Link(UUID minecraftId, String discordId, String minecraftName, Edition edition, Instant linkedAt) {
    public Link {
        DiscordIds.require(discordId, "discordId");
        if (minecraftName.isBlank()) {
            throw new IllegalArgumentException("minecraftName must not be blank");
        }
    }
}
