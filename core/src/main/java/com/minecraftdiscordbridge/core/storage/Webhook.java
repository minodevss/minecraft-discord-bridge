package com.minecraftdiscordbridge.core.storage;

import com.minecraftdiscordbridge.core.discord.DiscordId;

public record Webhook(DiscordId id, String token) {
    public Webhook {
        if (token.isBlank()) {
            throw new IllegalArgumentException("webhook token must not be blank");
        }
    }

    @Override
    public String toString() {
        return "Webhook[id=" + id + ", token=<hidden>]";
    }
}
