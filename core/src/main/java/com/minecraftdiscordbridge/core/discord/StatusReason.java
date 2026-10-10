package com.minecraftdiscordbridge.core.discord;

import java.util.Locale;

public record StatusReason(Kind kind, String detail) {
    public enum Kind {
        STARTING,
        CONNECTING,
        NO_TOKEN,
        CONFIG_ERRORS,
        STOPPED,
        START_FAILED,
        INVALID_TOKEN,
        UNREACHABLE,
        LOGIN_FAILED,
        DISCONNECTED,
        MESSAGE_CONTENT_OFF,
        MEMBERS_INTENT_OFF,
        INTENTS_REFUSED,
        NOT_IN_SERVER,
        CHANNELS_NOT_VISIBLE,
        NOT_IN_ANY_SERVER;

        public String key() {
            return name().toLowerCase(Locale.ROOT).replace('_', '-');
        }
    }

    public static StatusReason of(Kind kind) {
        return new StatusReason(kind, "");
    }
}
