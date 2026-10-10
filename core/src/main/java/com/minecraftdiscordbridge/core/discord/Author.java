package com.minecraftdiscordbridge.core.discord;

import org.jspecify.annotations.Nullable;

public record Author(String key, String name, @Nullable String avatarUrl) {
    static final String SERVER_KEY = "server";

    public static Author server(String name, @Nullable String avatarUrl) {
        return new Author(SERVER_KEY, name, avatarUrl);
    }

    public static Author player(String playerId, String name, @Nullable String avatarUrl) {
        return new Author("player:" + playerId, name, avatarUrl);
    }

    public boolean isServer() {
        return SERVER_KEY.equals(key);
    }
}
