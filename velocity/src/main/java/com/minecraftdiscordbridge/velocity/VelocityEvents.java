package com.minecraftdiscordbridge.velocity;

import com.minecraftdiscordbridge.core.Bridge;
import com.minecraftdiscordbridge.core.platform.GameEvent;
import com.minecraftdiscordbridge.core.platform.JoinDecision;
import com.minecraftdiscordbridge.core.platform.Permission;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

public final class VelocityEvents {
    private static final long GATE_WAIT_SECONDS = 4;

    private final Bridge bridge;
    private final VelocityProfiles profiles;

    VelocityEvents(Bridge bridge, VelocityProfiles profiles) {
        this.bridge = bridge;
        this.profiles = profiles;
    }

    @SuppressWarnings("deprecation")
    @Subscribe(order = PostOrder.LAST)
    public void onChat(PlayerChatEvent event) {
        PlayerChatEvent.ChatResult result = event.getResult();
        if (result.isAllowed()) {
            bridge.publish(new GameEvent.PlayerChatted(
                    profiles.of(event.getPlayer()), result.getMessage().orElse(event.getMessage())));
        }
    }

    @SuppressWarnings("deprecation")
    @Subscribe(order = PostOrder.LATE)
    public EventTask onLogin(LoginEvent event) {
        if (!event.getResult().isAllowed()) {
            return EventTask.resumeWhenComplete(CompletableFuture.completedFuture(null));
        }
        Player player = event.getPlayer();
        boolean bypass = player.getPermissionValue(Permission.GATE_BYPASS.node()) == Tristate.TRUE;
        CompletableFuture<Void> gate = bridge.checkJoin(profiles.of(player), bypass)
                .completeOnTimeout(new JoinDecision.Allow(), GATE_WAIT_SECONDS, TimeUnit.SECONDS)
                .thenAccept(decision -> {
                    if (decision instanceof JoinDecision.Deny deny) {
                        event.setResult(ResultedEvent.ComponentResult.denied(
                                GsonComponentSerializer.gson().deserialize(deny.json())));
                    }
                });
        return EventTask.resumeWhenComplete(gate);
    }

    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        bridge.publish(new GameEvent.PlayerJoined(profiles.of(event.getPlayer()), false));
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        if (joined(event.getLoginStatus())) {
            bridge.publish(new GameEvent.PlayerLeft(profiles.of(event.getPlayer()), false));
        }
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        bridge.publish(new GameEvent.PlayerSwitchedServer(
                profiles.of(event.getPlayer()),
                event.getPreviousServer().map(VelocityEvents::name).orElse(null),
                name(event.getServer())));
    }

    private static boolean joined(DisconnectEvent.LoginStatus status) {
        return status == DisconnectEvent.LoginStatus.SUCCESSFUL_LOGIN
                || status == DisconnectEvent.LoginStatus.PRE_SERVER_JOIN;
    }

    private static String name(RegisteredServer server) {
        return server.getServerInfo().getName();
    }
}
