package com.minecraftdiscordbridge.core.discord;

public record WebhookCredentials(long id, String token) {
    @Override
    public String toString() {
        return "WebhookCredentials[id=" + Long.toUnsignedString(id) + ", token=<hidden>]";
    }
}
