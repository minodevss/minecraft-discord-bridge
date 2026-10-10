package com.minecraftdiscordbridge.core.storage;

import java.util.Locale;

public enum ChannelRole {
    CHAT,
    EVENTS;

    String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    static ChannelRole fromId(String id) {
        for (ChannelRole role : values()) {
            if (role.id().equals(id)) {
                return role;
            }
        }
        throw new IllegalArgumentException("unknown channel role \"" + id + "\"; expected chat or events");
    }
}
