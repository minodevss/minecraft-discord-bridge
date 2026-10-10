package com.minecraftdiscordbridge.modded.neoforge;

import com.minecraftdiscordbridge.modded.common.LoaderHooks;
import com.minecraftdiscordbridge.modded.common.ModdedCommand;
import com.minecraftdiscordbridge.modded.common.ModdedEvents;
import com.minecraftdiscordbridge.modded.common.ModdedJoinGate;
import net.minecraft.server.level.ServerPlayer;
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
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;

@Mod(value = LoaderHooks.MOD_ID, dist = Dist.DEDICATED_SERVER)
public final class NeoForgeMod {
    public NeoForgeMod(IEventBus modBus) {
        NeoForgeLoaderHooks hooks = new NeoForgeLoaderHooks();
        ModdedEvents events = new ModdedEvents(hooks);
        NeoForgeJoinGate.register(modBus, new ModdedJoinGate(events));
        IEventBus bus = NeoForge.EVENT_BUS;
        bus.addListener(PermissionGatherEvent.Nodes.class, hooks::registerNodes);
        bus.addListener(RegisterCommandsEvent.class, event -> ModdedCommand.register(event.getDispatcher(), events));
        bus.addListener(ServerStartedEvent.class, event -> events.serverStarted(event.getServer()));
        bus.addListener(ServerStoppingEvent.class, event -> events.serverStopping());
        // NeoForge skips ServerStoppingEvent after a crash; ServerStoppedEvent always fires.
        bus.addListener(ServerStoppedEvent.class, event -> events.serverStopped());
        bus.addListener(
                EventPriority.LOWEST,
                false,
                ServerChatEvent.class,
                event -> events.playerChatted(event.getPlayer(), event.getRawText()));
        bus.addListener(PlayerEvent.PlayerLoggedInEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                events.playerJoined(player);
            }
        });
        bus.addListener(PlayerEvent.PlayerLoggedOutEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                events.playerLeft(player);
            }
        });
        bus.addListener(EventPriority.LOWEST, false, LivingDeathEvent.class, event -> {
            Entity entity = event.getEntity();
            if (entity instanceof ServerPlayer player) {
                events.playerDied(player);
            }
        });
        bus.addListener(AdvancementEvent.AdvancementEarnEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                events.advancementEarned(player, event.getAdvancement());
            }
        });
    }
}
