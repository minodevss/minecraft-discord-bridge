package com.minecraftdiscordbridge.modded.fabric.mixin;

import com.minecraftdiscordbridge.modded.fabric.FabricEntrypoint;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//? if >=1.21 {
import net.minecraft.advancements.AdvancementHolder;
//?} else
/*import net.minecraft.advancements.Advancement;*/

@Mixin(PlayerAdvancements.class)
abstract class PlayerAdvancementsMixin {
    @Shadow
    private ServerPlayer player;

    @Inject(
            method = "award",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/advancements/AdvancementRewards;grant(Lnet/minecraft/server/level/ServerPlayer;)V",
                            shift = At.Shift.AFTER))
    private void minecraftdiscordbridge$earned(
            //? if >=1.21 {
            AdvancementHolder advancement,
            //?} else {
            /*Advancement advancement,
            *///?}
            String criterion,
            CallbackInfoReturnable<Boolean> callback) {
        FabricEntrypoint.BRIDGE.advancementEarned(player, advancement);
    }
}
