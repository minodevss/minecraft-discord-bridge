package com.minecraftdiscordbridge.modded.fabric;

import com.minecraftdiscordbridge.modded.common.DiscordCommand;
import com.minecraftdiscordbridge.modded.common.ModdedBridge;
import com.minecraftdiscordbridge.modded.fabric.mixin.ServerLoginPacketListenerAccessor;
import com.mojang.authlib.GameProfile;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerLoginConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

public final class FabricEntrypoint implements DedicatedServerModInitializer {
    public static final ModdedBridge BRIDGE = new ModdedBridge(new FabricLoaderHooks());

    @Override
    public void onInitializeServer() {
        ServerLifecycleEvents.SERVER_STARTED.register(BRIDGE::serverStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> BRIDGE.serverStopping());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> BRIDGE.serverStopped());
        ServerMessageEvents.CHAT_MESSAGE.register(
                (message, sender, type) -> BRIDGE.playerChatted(sender, message.signedContent()));
        ServerLoginConnectionEvents.QUERY_START.register((handler, server, sender, synchronizer) -> {
            GameProfile profile = ((ServerLoginPacketListenerAccessor) handler).minecraftdiscordbridge$profile();
            if (profile != null) {
                synchronizer.waitFor(BRIDGE.checkJoin(profile).thenAccept(kick -> kick.ifPresent(handler::disconnect)));
            }
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> BRIDGE.playerJoined(handler.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> BRIDGE.playerLeft(handler.getPlayer()));
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registries, selection) -> DiscordCommand.register(dispatcher, BRIDGE));
    }
}
