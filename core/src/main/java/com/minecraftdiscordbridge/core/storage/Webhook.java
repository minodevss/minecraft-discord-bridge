package com.minecraftdiscordbridge.core.storage;

public record Webhook(String id, String token) {
    public Webhook {
        DiscordIds.require(id, "webhook id");
        if (token.isBlank()) {
            throw new IllegalArgumentException("webhook token must not be blank");
        }
    }

    @Override
    public String toString() {
        return "Webhook[id=" + id + ", token=<hidden>]";
    }
}
