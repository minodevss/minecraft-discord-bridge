package com.minecraftdiscordbridge.velocity;

import com.minecraftdiscordbridge.core.Bridge;
import com.minecraftdiscordbridge.core.platform.GameEvent;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.server.RegisteredServer;

public final class VelocityEvents {
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
