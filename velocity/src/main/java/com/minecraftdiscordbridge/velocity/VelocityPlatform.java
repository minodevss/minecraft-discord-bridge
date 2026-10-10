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
    private static final String NO_WHITELIST = "Whitelisting is not supported on a proxy";

    private final ProxyServer proxy;
    private final PlatformInfo info;
    private final Path dataDirectory;

    VelocityPlatform(ProxyServer proxy, PlatformInfo info, Path dataDirectory) {
        this.proxy = proxy;
        this.info = info;
        this.dataDirectory = dataDirectory;
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
        ActionOutcome outcome = switch (action) {
            case GameAction.WhitelistAdd add -> new ActionOutcome.Failed(NO_WHITELIST);
            case GameAction.WhitelistRemove remove -> new ActionOutcome.Failed(NO_WHITELIST);
            case GameAction.Kick kick -> kick(kick.playerName(), kick.reason());
        };
        return CompletableFuture.completedFuture(outcome);
    }

    private ActionOutcome kick(String name, String reason) {
        return proxy.getPlayer(name)
                .<ActionOutcome>map(player -> {
                    player.disconnect(Component.text(reason));
                    return new ActionOutcome.Done();
                })
                .orElseGet(() -> new ActionOutcome.NoSuchPlayer(name));
    }

    private static boolean seesChat(Player player) {
        return player.getPlayerSettings().getChatMode() == PlayerSettings.ChatMode.SHOWN;
    }
}
