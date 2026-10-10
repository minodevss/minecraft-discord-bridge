package com.minecraftdiscordbridge.paper;

import com.minecraftdiscordbridge.core.Bridge;
import com.minecraftdiscordbridge.core.platform.JoinDecision;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.slf4j.Logger;

public final class PaperJoinGate implements Listener {
    private static final long WAIT_SECONDS = 4;

    private final Bridge bridge;
    private final PaperProfiles profiles;
    private final Logger logger;

    PaperJoinGate(Bridge bridge, PaperProfiles profiles, Logger logger) {
        this.bridge = bridge;
        this.profiles = profiles;
        this.logger = logger;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        UUID id = event.getUniqueId();
        if (decide(id, event.getName()) instanceof JoinDecision.Deny deny) {
            event.disallow(
                    AsyncPlayerPreLoginEvent.Result.KICK_WHITELIST,
                    GsonComponentSerializer.gson().deserialize(deny.json()));
        }
    }

    private JoinDecision decide(UUID id, String name) {
        try {
            return bridge.checkJoin(
                            profiles.atLogin(id, name),
                            Bukkit.getOfflinePlayer(id).isOp())
                    .get(WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (ExecutionException | TimeoutException failure) {
            logger.warn("Let {} join because the Discord link check did not answer: {}", name, failure.toString());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        return new JoinDecision.Allow();
    }
}
