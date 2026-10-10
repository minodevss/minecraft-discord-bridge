package com.minecraftdiscordbridge.core.discord;

public record WebhookCredentials(DiscordId id, String token) {
    public WebhookCredentials {
        if (token.isBlank()) {
            throw new IllegalArgumentException("webhook token must not be blank");
        }
    }

    @Override
    public String toString() {
        return "WebhookCredentials[id=" + id + ", token=<hidden>]";
    }
}
