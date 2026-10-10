package com.minecraftdiscordbridge.core.platform;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public interface Platform {
    PlatformInfo info();

    Path dataDirectory();

    void deliver(Delivery delivery);

    CompletableFuture<ActionOutcome> perform(GameAction action);
}
