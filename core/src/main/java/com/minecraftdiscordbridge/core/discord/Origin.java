package com.minecraftdiscordbridge.core.discord;

enum Origin {
    SELF,
    OWN_WEBHOOK,
    WEBHOOK,
    BOT,
    USER;

    boolean relayed() {
        return this == USER;
    }
}
