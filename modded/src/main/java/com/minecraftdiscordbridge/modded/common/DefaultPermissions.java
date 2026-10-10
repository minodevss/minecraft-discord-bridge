package com.minecraftdiscordbridge.modded.common;

import com.minecraftdiscordbridge.core.platform.Permission;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;
//? if >=1.21.11 {
import net.minecraft.server.permissions.Permissions;
//?} else
/*import net.minecraft.commands.Commands;*/

public final class DefaultPermissions {
    private DefaultPermissions() {}

    public static boolean allows(CommandSourceStack source, Permission permission) {
        return permission.everyoneByDefault() || isAdmin(source);
    }

    public static boolean allows(@Nullable ServerPlayer player, Permission permission) {
        return permission.everyoneByDefault() || (player != null && isAdmin(player.createCommandSourceStack()));
    }

    //? if >=1.21.11 {
    private static boolean isAdmin(CommandSourceStack source) {
        return source.permissions().hasPermission(Permissions.COMMANDS_ADMIN);
    }
    //?} else {
    /*private static boolean isAdmin(CommandSourceStack source) {
        return source.hasPermission(Commands.LEVEL_ADMINS);
    }
    *///?}
}
