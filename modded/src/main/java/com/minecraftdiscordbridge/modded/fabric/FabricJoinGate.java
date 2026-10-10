package com.minecraftdiscordbridge.modded.fabric;

import com.minecraftdiscordbridge.modded.common.ModdedJoinGate;
import com.minecraftdiscordbridge.modded.fabric.mixin.ServerLoginPacketListenerAccessor;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.networking.v1.ServerLoginConnectionEvents;

final class FabricJoinGate {
    private FabricJoinGate() {}

    static void register(ModdedJoinGate gate) {
        ServerLoginConnectionEvents.QUERY_START.register((handler, server, sender, synchronizer) -> {
            GameProfile profile = ((ServerLoginPacketListenerAccessor) handler).minecraftdiscordbridge$profile();
            if (profile != null) {
                synchronizer.waitFor(gate.check(profile).thenAccept(kick -> kick.ifPresent(handler::disconnect)));
            }
        });
    }
}
