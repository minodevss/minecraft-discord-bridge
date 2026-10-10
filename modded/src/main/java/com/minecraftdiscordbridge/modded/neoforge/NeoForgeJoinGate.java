package com.minecraftdiscordbridge.modded.neoforge;

import com.minecraftdiscordbridge.modded.common.LoaderHooks;
import com.minecraftdiscordbridge.modded.common.ModdedJoinGate;
import java.util.function.Consumer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterConfigurationTasksEvent;

final class NeoForgeJoinGate implements ConfigurationTask {
    static final ConfigurationTask.Type TYPE = new ConfigurationTask.Type(LoaderHooks.MOD_ID + ":join_gate");

    private final ServerConfigurationPacketListenerImpl listener;
    private final ModdedJoinGate gate;

    private NeoForgeJoinGate(ServerConfigurationPacketListenerImpl listener, ModdedJoinGate gate) {
        this.listener = listener;
        this.gate = gate;
    }

    static void register(IEventBus modBus, ModdedJoinGate gate) {
        modBus.addListener(RegisterConfigurationTasksEvent.class, event -> {
            if (event.getListener() instanceof ServerConfigurationPacketListenerImpl listener) {
                event.register(new NeoForgeJoinGate(listener, gate));
            }
        });
    }

    @Override
    public void start(Consumer<Packet<?>> sender) {
        var unused = gate.check(listener.getOwner()).thenAccept(kick -> {
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
