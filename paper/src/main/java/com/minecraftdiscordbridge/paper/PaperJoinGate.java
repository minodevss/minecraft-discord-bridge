package com.minecraftdiscordbridge.paper;

import com.minecraftdiscordbridge.core.Bridge;
import com.minecraftdiscordbridge.core.platform.JoinDecision;
import com.minecraftdiscordbridge.core.platform.PlayerProfile;
import java.util.UUID;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

public final class PaperJoinGate implements Listener {
    private final Bridge bridge;
    private final PaperProfiles profiles;

    PaperJoinGate(Bridge bridge, PaperProfiles profiles) {
        this.bridge = bridge;
        this.profiles = profiles;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        UUID id = event.getUniqueId();
        PlayerProfile player = profiles.atLogin(id, event.getName());
        JoinDecision decision =
                bridge.checkJoin(player, Bukkit.getOfflinePlayer(id).isOp()).join();
        if (decision instanceof JoinDecision.Deny deny) {
            event.disallow(
                    AsyncPlayerPreLoginEvent.Result.KICK_WHITELIST,
                    GsonComponentSerializer.gson().deserialize(deny.json()));
        }
    }
}
