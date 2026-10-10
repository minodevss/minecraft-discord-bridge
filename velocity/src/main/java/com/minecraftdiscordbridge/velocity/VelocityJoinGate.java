package com.minecraftdiscordbridge.velocity;

import com.minecraftdiscordbridge.core.Bridge;
import com.minecraftdiscordbridge.core.platform.JoinDecision;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.proxy.Player;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

public final class VelocityJoinGate {
    private final Bridge bridge;
    private final VelocityProfiles profiles;

    VelocityJoinGate(Bridge bridge, VelocityProfiles profiles) {
        this.bridge = bridge;
        this.profiles = profiles;
    }

    @SuppressWarnings("deprecation")
    @Subscribe(order = PostOrder.LATE)
    public EventTask onLogin(LoginEvent event) {
        if (!event.getResult().isAllowed()) {
            return EventTask.resumeWhenComplete(CompletableFuture.completedFuture(null));
        }
        Player player = event.getPlayer();
        VelocityCommandSource source = new VelocityCommandSource(player);
        CompletableFuture<Void> gate = bridge.checkJoin(
                        profiles.of(player),
                        permission -> CompletableFuture.completedFuture(source.hasPermission(permission)))
                .thenAccept(decision -> {
                    if (decision instanceof JoinDecision.Deny deny) {
                        event.setResult(ResultedEvent.ComponentResult.denied(
                                GsonComponentSerializer.gson().deserialize(deny.json())));
                    }
                });
        return EventTask.resumeWhenComplete(gate);
    }
}
