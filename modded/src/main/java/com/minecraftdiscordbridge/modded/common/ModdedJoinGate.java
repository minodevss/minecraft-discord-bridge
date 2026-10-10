package com.minecraftdiscordbridge.modded.common;

import com.minecraftdiscordbridge.core.platform.JoinDecision;
import com.mojang.authlib.GameProfile;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
//? if >=1.21.11 {
import net.minecraft.server.players.NameAndId;
//?}

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
        return current.bridge()
                .checkJoin(current.profiles().of(profile), isOperator(server, profile))
                .thenApplyAsync(decision -> kickMessage(decision, server), server);
    }

    private static Optional<Component> kickMessage(JoinDecision decision, MinecraftServer server) {
        if (decision instanceof JoinDecision.Deny deny) {
            return Optional.of(
                    Texts.parse(deny.json(), server.registryAccess()).orElseGet(() -> Component.literal(REFUSED)));
        }
        return Optional.empty();
    }

    //? if >=1.21.11 {
    private static boolean isOperator(MinecraftServer server, GameProfile profile) {
        return server.getPlayerList().isOp(new NameAndId(profile));
    }
    //?} else {
    /*private static boolean isOperator(MinecraftServer server, GameProfile profile) {
        return server.getPlayerList().isOp(profile);
    }
    *///?}
}
