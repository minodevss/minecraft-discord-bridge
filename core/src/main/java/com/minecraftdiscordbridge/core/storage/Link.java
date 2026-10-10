package com.minecraftdiscordbridge.core.storage;

import com.minecraftdiscordbridge.core.discord.DiscordId;
import java.time.Instant;
import java.util.UUID;

public record Link(UUID minecraftId, DiscordId discordId, String minecraftName, Edition edition, Instant linkedAt) {
    public Link {
        if (minecraftName.isBlank()) {
            throw new IllegalArgumentException("minecraftName must not be blank");
        }
    }
}
