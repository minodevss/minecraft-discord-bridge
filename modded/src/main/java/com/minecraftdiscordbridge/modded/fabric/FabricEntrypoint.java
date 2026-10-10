package com.minecraftdiscordbridge.modded.fabric;

import com.minecraftdiscordbridge.modded.common.DiscordCommand;
import com.minecraftdiscordbridge.modded.common.ModdedBridge;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
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
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> BRIDGE.playerJoined(handler.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> BRIDGE.playerLeft(handler.getPlayer()));
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registries, selection) -> DiscordCommand.register(dispatcher, BRIDGE));
    }
}
