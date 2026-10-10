package com.minecraftdiscordbridge.paper;

import com.destroystokyo.paper.ClientOption;
import com.minecraftdiscordbridge.core.platform.ActionOutcome;
import com.minecraftdiscordbridge.core.platform.Delivery;
import com.minecraftdiscordbridge.core.platform.GameAction;
import com.minecraftdiscordbridge.core.platform.Platform;
import com.minecraftdiscordbridge.core.platform.PlatformInfo;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

final class PaperPlatform implements Platform {
    private final PlatformInfo info;
    private final Path dataDirectory;
    private final PaperActions actions;

    PaperPlatform(PlatformInfo info, Path dataDirectory, PaperActions actions) {
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
        for (Player player : Bukkit.getOnlinePlayers()) {
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
        return player.getClientOption(ClientOption.CHAT_VISIBILITY) == ClientOption.ChatVisibility.FULL;
    }
}
