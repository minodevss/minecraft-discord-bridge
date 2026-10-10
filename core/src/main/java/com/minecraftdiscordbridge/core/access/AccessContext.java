package com.minecraftdiscordbridge.core.access;

import com.minecraftdiscordbridge.core.discord.Discord;
import com.minecraftdiscordbridge.core.linking.PendingLinks;
import com.minecraftdiscordbridge.core.storage.LinkStore;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.concurrent.Executor;

public record AccessContext(
        LinkStore links,
        PendingLinks pending,
        Discord discord,
        PermissionGroups groups,
        AccessMemory memory,
        InstantSource clock,
        Executor loop,
        Scheduler later) {
    @FunctionalInterface
    public interface Scheduler {
        void schedule(Duration delay, Runnable task);
    }

    Instant now() {
        return clock.instant();
    }
}
