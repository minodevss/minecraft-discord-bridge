package com.minecraftdiscordbridge.core.discord;

import java.util.Optional;

public record Author(String key, String name, Optional<String> avatarUrl) {
    static final String SERVER_KEY = "server";

    public static Author server() {
        return new Author(SERVER_KEY, "", Optional.empty());
    }

    public static Author server(String name, Optional<String> avatarUrl) {
        return new Author(SERVER_KEY, name, avatarUrl);
    }

    public static Author player(String playerId, String name, Optional<String> avatarUrl) {
        return new Author("player:" + playerId, name, avatarUrl);
    }

    public boolean isServer() {
        return SERVER_KEY.equals(key);
    }
}
