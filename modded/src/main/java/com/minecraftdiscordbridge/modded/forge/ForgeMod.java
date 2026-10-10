package com.minecraftdiscordbridge.modded.forge;

import com.minecraftdiscordbridge.modded.common.LoaderHooks;
import com.minecraftdiscordbridge.modded.common.ModdedCommand;
import com.minecraftdiscordbridge.modded.common.ModdedEvents;
import com.minecraftdiscordbridge.modded.common.ModdedJoinGate;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.AdvancementEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.server.permission.events.PermissionGatherEvent;

@Mod(LoaderHooks.MOD_ID)
public final class ForgeMod {
    public ForgeMod() {
        if (!FMLEnvironment.dist.isDedicatedServer()) {
            return;
        }
        ForgeLoaderHooks hooks = new ForgeLoaderHooks();
        ModdedEvents events = new ModdedEvents(hooks);
        IEventBus bus = MinecraftForge.EVENT_BUS;
        bus.addListener(EventPriority.NORMAL, false, PermissionGatherEvent.Nodes.class, hooks::registerNodes);
        bus.addListener(
                EventPriority.NORMAL,
                false,
                RegisterCommandsEvent.class,
                event -> ModdedCommand.register(event.getDispatcher(), events));
        bus.addListener(
                EventPriority.NORMAL,
                false,
                ServerStartedEvent.class,
                event -> events.serverStarted(event.getServer()));
        bus.addListener(EventPriority.NORMAL, false, ServerStoppingEvent.class, event -> events.serverStopping());
        // Forge skips ServerStoppingEvent after a crash; ServerStoppedEvent always fires.
        bus.addListener(EventPriority.NORMAL, false, ServerStoppedEvent.class, event -> events.serverStopped());
        bus.addListener(
                EventPriority.LOWEST,
                false,
                ServerChatEvent.class,
                event -> events.playerChatted(event.getPlayer(), event.getRawText()));
        ForgeJoinGate.register(bus, new ModdedJoinGate(events));
        bus.addListener(EventPriority.NORMAL, false, PlayerEvent.PlayerLoggedInEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                events.playerJoined(player);
            }
        });
        bus.addListener(EventPriority.NORMAL, false, PlayerEvent.PlayerLoggedOutEvent.class, event -> {
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
        bus.addListener(EventPriority.NORMAL, false, AdvancementEvent.AdvancementEarnEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                events.advancementEarned(player, event.getAdvancement());
            }
        });
    }
}
