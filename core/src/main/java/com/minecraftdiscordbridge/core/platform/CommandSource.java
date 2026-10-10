package com.minecraftdiscordbridge.core.platform;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

public interface CommandSource {
    @Nullable
    UUID playerId();

    String name();

    boolean hasPermission(Permission permission);

    void reply(String json);
}
