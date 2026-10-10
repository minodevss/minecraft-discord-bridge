package com.minecraftdiscordbridge.modded.neoforge;

import com.minecraftdiscordbridge.modded.common.DiscordCommand;
import com.minecraftdiscordbridge.modded.common.ModdedBridge;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.network.event.RegisterConfigurationTasksEvent;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;

@Mod(value = ModdedBridge.MOD_ID, dist = Dist.DEDICATED_SERVER)
public final class NeoForgeEntrypoint {
    public NeoForgeEntrypoint(IEventBus modBus) {
        NeoForgeLoaderHooks hooks = new NeoForgeLoaderHooks();
        ModdedBridge bridge = new ModdedBridge(hooks);
        modBus.addListener(RegisterConfigurationTasksEvent.class, event -> {
            if (event.getListener() instanceof ServerConfigurationPacketListenerImpl listener) {
                event.register(new JoinGateTask(listener, bridge));
            }
        });
        IEventBus bus = NeoForge.EVENT_BUS;
        bus.addListener(PermissionGatherEvent.Nodes.class, hooks::registerNodes);
        bus.addListener(RegisterCommandsEvent.class, event -> DiscordCommand.register(event.getDispatcher(), bridge));
        bus.addListener(ServerStartedEvent.class, event -> bridge.serverStarted(event.getServer()));
        bus.addListener(ServerStoppingEvent.class, event -> bridge.serverStopping());
        // NeoForge skips ServerStoppingEvent after a crash; ServerStoppedEvent always fires.
        bus.addListener(ServerStoppedEvent.class, event -> bridge.serverStopped());
        bus.addListener(
                EventPriority.LOWEST,
                false,
                ServerChatEvent.class,
                event -> bridge.playerChatted(event.getPlayer(), event.getRawText()));
        bus.addListener(PlayerEvent.PlayerLoggedInEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                bridge.playerJoined(player);
            }
        });
        bus.addListener(PlayerEvent.PlayerLoggedOutEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                bridge.playerLeft(player);
            }
        });
        bus.addListener(EventPriority.LOWEST, false, LivingDeathEvent.class, event -> {
            Entity entity = event.getEntity();
            if (entity instanceof ServerPlayer player) {
                bridge.playerDied(player);
            }
        });
        bus.addListener(AdvancementEvent.AdvancementEarnEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                bridge.advancementEarned(player, event.getAdvancement());
            }
        });
    }
}
