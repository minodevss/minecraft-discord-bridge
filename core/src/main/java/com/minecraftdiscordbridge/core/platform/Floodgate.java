package com.minecraftdiscordbridge.core.platform;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.function.Predicate;

public final class Floodgate {
    private static final String API = "org.geysermc.floodgate.api.FloodgateApi";

    private final Method instance;
    private final Method isFloodgatePlayer;

    private Floodgate(Method instance, Method isFloodgatePlayer) {
        this.instance = instance;
        this.isFloodgatePlayer = isFloodgatePlayer;
    }

    public static Predicate<UUID> bedrockCheck() {
        return bedrockCheck(API);
    }

    static Predicate<UUID> bedrockCheck(String api) {
        try {
            Class<?> type = Class.forName(api);
            return new Floodgate(type.getMethod("getInstance"), type.getMethod("isFloodgatePlayer", UUID.class))
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
