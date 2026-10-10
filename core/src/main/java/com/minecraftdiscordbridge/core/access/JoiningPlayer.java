package com.minecraftdiscordbridge.core.access;

import static com.minecraftdiscordbridge.core.log.Log.LOGGER;

import com.minecraftdiscordbridge.core.log.Failures;
import com.minecraftdiscordbridge.core.platform.OfflinePermissions;
import com.minecraftdiscordbridge.core.platform.Permission;
import com.minecraftdiscordbridge.core.platform.PlayerProfile;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;

public final class JoiningPlayer {
    private static final Permission BYPASS = Permission.GATE_BYPASS;

    private final PlayerProfile profile;
    private final OfflinePermissions permissions;
    private final PermissionGroups groups;
    private final AtomicReference<@Nullable CompletableFuture<Boolean>> bypass = new AtomicReference<>();

    public JoiningPlayer(PlayerProfile profile, OfflinePermissions permissions, PermissionGroups groups) {
        this.profile = profile;
        this.permissions = permissions;
        this.groups = groups;
    }

    public PlayerProfile profile() {
        return profile;
    }

    public CompletableFuture<Boolean> bypass() {
        CompletableFuture<Boolean> started = bypass.get();
        if (started != null) {
            return started;
        }
        CompletableFuture<Boolean> lookup = new CompletableFuture<>();
        if (!bypass.compareAndSet(null, lookup)) {
            return Objects.requireNonNull(bypass.get());
        }
        var unused = lookUp().whenComplete((granted, failure) -> {
            if (failure != null) {
                LOGGER.warn(
                        "Could not check whether {} has {}: {}. They go through the join gate",
                        profile.name(),
                        BYPASS.node(),
                        Failures.describe(failure));
            }
            lookup.complete(Boolean.TRUE.equals(granted));
        });
        return lookup;
    }

    public boolean bypassed() {
        CompletableFuture<Boolean> started = bypass.get();
        return started != null && started.getNow(false);
    }

    private CompletableFuture<Boolean> lookUp() {
        return groups.permission(profile.id(), BYPASS.node())
                .exceptionally(unavailable -> Optional.empty())
                .thenCompose(set -> set.map(CompletableFuture::completedFuture).orElseGet(this::askPlatform));
    }

    private CompletableFuture<Boolean> askPlatform() {
        try {
            return permissions.has(BYPASS);
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }
}
