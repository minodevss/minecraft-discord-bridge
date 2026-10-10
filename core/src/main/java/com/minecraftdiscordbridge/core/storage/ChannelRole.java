package com.minecraftdiscordbridge.core.storage;

import java.util.Optional;

public enum ChannelRole {
    CHAT,
    EVENTS;

    public String id() {
        return EnumIds.id(this);
    }

    public static Optional<ChannelRole> find(String id) {
        return EnumIds.find(ChannelRole.class, id);
    }

    static ChannelRole fromId(String id) {
        return EnumIds.fromId(ChannelRole.class, id, "channel role");
    }
}
