package com.minecraftdiscordbridge.core.discord;

enum Origin {
    SELF,
    OWN_WEBHOOK,
    WEBHOOK,
    BOT,
    USER;

    boolean relayed(boolean relayBots, boolean relayWebhooks) {
        return switch (this) {
            case SELF, OWN_WEBHOOK -> false;
            case WEBHOOK -> relayWebhooks;
            case BOT -> relayBots;
            case USER -> true;
        };
    }
}
