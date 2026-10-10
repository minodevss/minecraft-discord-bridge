package com.minecraftdiscordbridge.modded.neoforge;

import com.minecraftdiscordbridge.modded.common.ModdedBridge;
import java.util.function.Consumer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;

final class JoinGateTask implements ConfigurationTask {
    static final ConfigurationTask.Type TYPE = new ConfigurationTask.Type(ModdedBridge.MOD_ID + ":join_gate");

    private final ServerConfigurationPacketListenerImpl listener;
    private final ModdedBridge bridge;

    JoinGateTask(ServerConfigurationPacketListenerImpl listener, ModdedBridge bridge) {
        this.listener = listener;
        this.bridge = bridge;
    }

    @Override
    public void start(Consumer<Packet<?>> sender) {
        var unused = bridge.checkJoin(listener.getOwner()).thenAccept(kick -> {
            if (!listener.isAcceptingMessages()) {
                return;
            }
            if (kick.isPresent()) {
                listener.disconnect(kick.get());
            } else {
                listener.finishCurrentTask(TYPE);
            }
        });
    }

    @Override
    public ConfigurationTask.Type type() {
        return TYPE;
    }
}
