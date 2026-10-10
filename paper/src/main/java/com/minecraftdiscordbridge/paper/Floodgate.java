package com.minecraftdiscordbridge.paper;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.function.Predicate;

final class Floodgate {
    private static final String API = "org.geysermc.floodgate.api.FloodgateApi";

    private final Method instance;
    private final Method isFloodgatePlayer;

    private Floodgate(Method instance, Method isFloodgatePlayer) {
        this.instance = instance;
        this.isFloodgatePlayer = isFloodgatePlayer;
    }

    static Predicate<UUID> bedrockCheck() {
        try {
            Class<?> api = Class.forName(API);
            return new Floodgate(api.getMethod("getInstance"), api.getMethod("isFloodgatePlayer", UUID.class))
                    ::isBedrock;
        } catch (ReflectiveOperationException | LinkageError absent) {
            return id -> false;
        }
    }

    private boolean isBedrock(UUID id) {
        try {
            return Boolean.TRUE.equals(isFloodgatePlayer.invoke(instance.invoke(null), id));
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            return false;
        }
    }
}
