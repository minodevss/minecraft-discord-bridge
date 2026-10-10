package com.minecraftdiscordbridge.modded.forge;

import com.minecraftdiscordbridge.modded.common.ModdedJoinGate;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import net.minecraftforge.event.entity.player.PlayerNegotiationEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;

final class ForgeJoinGate {
    private ForgeJoinGate() {}

    static void register(IEventBus bus, ModdedJoinGate gate) {
        bus.addListener(EventPriority.NORMAL, false, PlayerNegotiationEvent.class, event -> {
            Connection connection = event.getConnection();
            event.enqueueWork(gate.check(event.getProfile())
                    .thenAccept(kick -> kick.ifPresent(message -> disconnect(connection, message))));
        });
    }

    private static void disconnect(Connection connection, Component message) {
        if (connection.getPacketListener() instanceof ServerLoginPacketListenerImpl login) {
            login.disconnect(message);
        } else {
            connection.disconnect(message);
        }
    }
}
