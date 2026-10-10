package com.minecraftdiscordbridge.core.access;

import com.minecraftdiscordbridge.core.discord.Discord;
import com.minecraftdiscordbridge.core.linking.PendingLinks;
import com.minecraftdiscordbridge.core.loop.Scheduler;
import com.minecraftdiscordbridge.core.storage.LinkStore;
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
        Scheduler scheduler) {
    Instant now() {
        return clock.instant();
    }
}
