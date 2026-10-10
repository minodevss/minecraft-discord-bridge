package com.minecraftdiscordbridge.modded.neoforge;

import com.minecraftdiscordbridge.modded.common.LoaderHooks;
import com.minecraftdiscordbridge.modded.common.ModdedEvents;
import java.util.function.Consumer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;

final class JoinGateTask implements ConfigurationTask {
    static final ConfigurationTask.Type TYPE = new ConfigurationTask.Type(LoaderHooks.MOD_ID + ":join_gate");

    private final ServerConfigurationPacketListenerImpl listener;
    private final ModdedEvents events;

    JoinGateTask(ServerConfigurationPacketListenerImpl listener, ModdedEvents events) {
        this.listener = listener;
        this.events = events;
    }

    @Override
    public void start(Consumer<Packet<?>> sender) {
        var unused = events.checkJoin(listener.getOwner()).thenAccept(kick -> {
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
