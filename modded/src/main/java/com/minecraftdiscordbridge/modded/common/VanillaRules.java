package com.minecraftdiscordbridge.modded.common;

import net.minecraft.server.level.ServerPlayer;
//? if >=1.21.11 {
import net.minecraft.world.level.gamerules.GameRules;
//?} else
/*import net.minecraft.world.level.GameRules;*/

final class VanillaRules {
    private VanillaRules() {}

    //? if >=1.21.11 {
    static boolean showDeathMessages(ServerPlayer player) {
        return player.level().getGameRules().get(GameRules.SHOW_DEATH_MESSAGES);
    }

    static boolean announceAdvancements(ServerPlayer player) {
        return player.level().getGameRules().get(GameRules.SHOW_ADVANCEMENT_MESSAGES);
    }
    //?} else {
    /*static boolean showDeathMessages(ServerPlayer player) {
        return player.level().getGameRules().getBoolean(GameRules.RULE_SHOWDEATHMESSAGES);
    }

    static boolean announceAdvancements(ServerPlayer player) {
        return player.level().getGameRules().getBoolean(GameRules.RULE_ANNOUNCE_ADVANCEMENTS);
    }
    *///?}
}
