package com.minecraftdiscordbridge.modded.forge;

import com.minecraftdiscordbridge.modded.common.DiscordCommand;
import com.minecraftdiscordbridge.modded.common.ModdedBridge;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.AdvancementEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerNegotiationEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.server.permission.events.PermissionGatherEvent;

@Mod(ModdedBridge.MOD_ID)
public final class ForgeEntrypoint {
    public ForgeEntrypoint() {
        if (!FMLEnvironment.dist.isDedicatedServer()) {
            return;
        }
        ForgeLoaderHooks hooks = new ForgeLoaderHooks();
        ModdedBridge bridge = new ModdedBridge(hooks);
        IEventBus bus = MinecraftForge.EVENT_BUS;
        bus.addListener(EventPriority.NORMAL, false, PermissionGatherEvent.Nodes.class, hooks::registerNodes);
        bus.addListener(
                EventPriority.NORMAL,
                false,
                RegisterCommandsEvent.class,
                event -> DiscordCommand.register(event.getDispatcher(), bridge));
        bus.addListener(
                EventPriority.NORMAL,
                false,
                ServerStartedEvent.class,
                event -> bridge.serverStarted(event.getServer()));
        bus.addListener(EventPriority.NORMAL, false, ServerStoppingEvent.class, event -> bridge.serverStopping());
        // Forge skips ServerStoppingEvent after a crash; ServerStoppedEvent always fires.
        bus.addListener(EventPriority.NORMAL, false, ServerStoppedEvent.class, event -> bridge.serverStopped());
        bus.addListener(
                EventPriority.LOWEST,
                false,
                ServerChatEvent.class,
                event -> bridge.playerChatted(event.getPlayer(), event.getRawText()));
        bus.addListener(EventPriority.NORMAL, false, PlayerNegotiationEvent.class, event -> {
            Connection connection = event.getConnection();
            event.enqueueWork(bridge.checkJoin(event.getProfile())
                    .thenAccept(kick -> kick.ifPresent(message -> disconnect(connection, message))));
        });
        bus.addListener(EventPriority.NORMAL, false, PlayerEvent.PlayerLoggedInEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                bridge.playerJoined(player);
            }
        });
        bus.addListener(EventPriority.NORMAL, false, PlayerEvent.PlayerLoggedOutEvent.class, event -> {
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
        bus.addListener(EventPriority.NORMAL, false, AdvancementEvent.AdvancementEarnEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                bridge.advancementEarned(player, event.getAdvancement());
            }
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
