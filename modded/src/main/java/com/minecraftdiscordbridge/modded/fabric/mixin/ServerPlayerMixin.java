package com.minecraftdiscordbridge.modded.fabric.mixin;

import com.minecraftdiscordbridge.modded.fabric.FabricEntrypoint;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
abstract class ServerPlayerMixin {
    @Inject(method = "die", at = @At("HEAD"))
    private void minecraftdiscordbridge$died(DamageSource source, CallbackInfo callback) {
        FabricEntrypoint.BRIDGE.playerDied((ServerPlayer) (Object) this);
    }
}
