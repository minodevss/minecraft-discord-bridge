package com.minecraftdiscordbridge.modded.fabric.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerLoginPacketListenerImpl.class)
public interface ServerLoginPacketListenerAccessor {
    //? if >=1.21 {
    @Accessor("authenticatedProfile")
    //?} else
    /*@Accessor("gameProfile")*/
    @Nullable
    GameProfile minecraftdiscordbridge$profile();
}
