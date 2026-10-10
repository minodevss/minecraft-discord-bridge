package com.minecraftdiscordbridge.velocity;

import com.minecraftdiscordbridge.core.platform.ActionOutcome;
import com.minecraftdiscordbridge.core.platform.Delivery;
import com.minecraftdiscordbridge.core.platform.GameAction;
import com.minecraftdiscordbridge.core.platform.Platform;
import com.minecraftdiscordbridge.core.platform.PlatformInfo;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.player.PlayerSettings;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

final class VelocityPlatform implements Platform {
    private final ProxyServer proxy;
    private final PlatformInfo info;
    private final Path dataDirectory;
    private final VelocityActions actions;

    VelocityPlatform(ProxyServer proxy, PlatformInfo info, Path dataDirectory, VelocityActions actions) {
        this.proxy = proxy;
        this.info = info;
        this.dataDirectory = dataDirectory;
        this.actions = actions;
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
        Component message = GsonComponentSerializer.gson().deserialize(delivery.json());
        for (Player player : proxy.getAllPlayers()) {
            if (!delivery.excluded().contains(player.getUniqueId()) && seesChat(player)) {
                player.sendMessage(message);
            }
        }
    }

    @Override
    public CompletableFuture<ActionOutcome> perform(GameAction action) {
        return actions.perform(action);
    }

    private static boolean seesChat(Player player) {
        return player.getPlayerSettings().getChatMode() == PlayerSettings.ChatMode.SHOWN;
    }
}
