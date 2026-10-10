package com.minecraftdiscordbridge.modded.fabric;

import com.minecraftdiscordbridge.modded.common.ModdedCommand;
import com.minecraftdiscordbridge.modded.common.ModdedEvents;
import com.minecraftdiscordbridge.modded.fabric.mixin.ServerLoginPacketListenerAccessor;
import com.mojang.authlib.GameProfile;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerLoginConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

public final class FabricMod implements DedicatedServerModInitializer {
    public static final ModdedEvents EVENTS = new ModdedEvents(new FabricLoaderHooks());

    @Override
    public void onInitializeServer() {
        ServerLifecycleEvents.SERVER_STARTED.register(EVENTS::serverStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> EVENTS.serverStopping());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> EVENTS.serverStopped());
        ServerMessageEvents.CHAT_MESSAGE.register(
                (message, sender, type) -> EVENTS.playerChatted(sender, message.signedContent()));
        ServerLoginConnectionEvents.QUERY_START.register((handler, server, sender, synchronizer) -> {
            GameProfile profile = ((ServerLoginPacketListenerAccessor) handler).minecraftdiscordbridge$profile();
            if (profile != null) {
                synchronizer.waitFor(EVENTS.checkJoin(profile).thenAccept(kick -> kick.ifPresent(handler::disconnect)));
            }
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> EVENTS.playerJoined(handler.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> EVENTS.playerLeft(handler.getPlayer()));
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registries, selection) -> ModdedCommand.register(dispatcher, EVENTS));
    }
}
