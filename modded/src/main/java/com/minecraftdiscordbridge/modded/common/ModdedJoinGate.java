package com.minecraftdiscordbridge.modded.common;

import com.minecraftdiscordbridge.core.platform.JoinDecision;
import com.minecraftdiscordbridge.core.platform.PlayerProfile;
import com.mojang.authlib.GameProfile;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

public final class ModdedJoinGate {
    private static final String REFUSED = "You cannot join this server right now.";

    private final ModdedEvents events;

    public ModdedJoinGate(ModdedEvents events) {
        this.events = events;
    }

    public CompletableFuture<Optional<Component>> check(GameProfile profile) {
        ModdedEvents.Session current = events.session();
        if (current == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        MinecraftServer server = current.server();
        PlayerProfile player = current.profiles().of(profile);
        LoaderHooks loader = events.loader();
        return current.bridge()
                .checkJoin(player, permission -> loader.hasOfflinePermission(server, player.id(), permission))
                .thenApplyAsync(decision -> kickMessage(decision, server), server);
    }

    private static Optional<Component> kickMessage(JoinDecision decision, MinecraftServer server) {
        if (decision instanceof JoinDecision.Deny deny) {
            return Optional.of(
                    Texts.parse(deny.json(), server.registryAccess()).orElseGet(() -> Component.literal(REFUSED)));
        }
        return Optional.empty();
    }
}
