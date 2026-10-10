package com.minecraftdiscordbridge.modded.common;

import com.minecraftdiscordbridge.core.platform.ActionOutcome;
import com.minecraftdiscordbridge.core.platform.Delivery;
import com.minecraftdiscordbridge.core.platform.GameAction;
import com.minecraftdiscordbridge.core.platform.Platform;
import com.minecraftdiscordbridge.core.platform.PlatformInfo;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.ChatVisiblity;

final class ModdedPlatform implements Platform {
    private final MinecraftServer server;
    private final PlatformInfo info;
    private final Path dataDirectory;
    private final GameActions actions;

    ModdedPlatform(MinecraftServer server, LoaderHooks loader) {
        this.server = server;
        this.info = new PlatformInfo(
                loader.software(), server.getServerVersion(), dataVersion(), server.usesAuthentication());
        this.dataDirectory = loader.dataDirectory();
        this.actions = new GameActions(server);
    }

    @Override
    public PlatformInfo info() {
        return info;
    }

    @Override
    public Path dataDirectory() {
        return dataDirectory;
    }

    @Override
    public void deliver(Delivery delivery) {
        ServerThread.run(server, () -> Texts.parse(delivery.json(), server.registryAccess())
                .ifPresent(message -> send(message, delivery.excluded())));
    }

    @Override
    public CompletableFuture<ActionOutcome> perform(GameAction action) {
        return actions.perform(action);
    }

    private void send(Component message, Set<UUID> excluded) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!excluded.contains(player.getUUID()) && player.getChatVisibility() == ChatVisiblity.FULL) {
                player.sendSystemMessage(message);
            }
        }
    }

    //? if >=1.21.11 {
    private static int dataVersion() {
        return SharedConstants.getCurrentVersion().dataVersion().version();
    }
    //?} else {
    /*private static int dataVersion() {
        return SharedConstants.getCurrentVersion().getDataVersion().getVersion();
    }
    *///?}
}
