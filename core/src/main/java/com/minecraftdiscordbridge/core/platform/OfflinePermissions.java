package com.minecraftdiscordbridge.core.platform;

import java.util.concurrent.CompletableFuture;

@FunctionalInterface
public interface OfflinePermissions {
    // Core asks on its own loop, so an answer that needs I/O must come back as a future.
    CompletableFuture<Boolean> has(Permission permission);
}
