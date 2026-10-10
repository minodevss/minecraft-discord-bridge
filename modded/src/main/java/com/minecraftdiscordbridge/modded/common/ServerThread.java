package com.minecraftdiscordbridge.modded.common;

import com.minecraftdiscordbridge.core.platform.ActionOutcome;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.minecraft.server.MinecraftServer;

final class ServerThread {
    private ServerThread() {}

    static void run(MinecraftServer server, Runnable task) {
        server.execute(() -> {
            if (server.isSameThread()) {
                task.run();
            }
        });
    }

    static CompletableFuture<ActionOutcome> supply(MinecraftServer server, Supplier<ActionOutcome> task) {
        CompletableFuture<ActionOutcome> outcome = new CompletableFuture<>();
        server.execute(() -> {
            if (!server.isSameThread()) {
                outcome.complete(new ActionOutcome.ServerStopping());
                return;
            }
            try {
                outcome.complete(task.get());
            } catch (RuntimeException error) {
                outcome.completeExceptionally(error);
            }
        });
        return outcome;
    }
}
