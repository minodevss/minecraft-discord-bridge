package com.minecraftdiscordbridge.modded.common;

import com.minecraftdiscordbridge.core.platform.Permission;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;
//? if >=1.21.11 {
import net.minecraft.server.permissions.Permissions;
import net.minecraft.server.players.NameAndId;
//?} else {
/*import com.mojang.authlib.GameProfile;
import net.minecraft.commands.Commands;
*///?}

public final class DefaultPermissions {
    private DefaultPermissions() {}

    public static boolean allows(CommandSourceStack source, Permission permission) {
        return permission.everyoneByDefault() || isAdmin(source);
    }

    public static boolean allows(ServerPlayer player, Permission permission) {
        return allows(player.createCommandSourceStack(), permission);
    }

    public static boolean allowsOffline(@Nullable MinecraftServer server, UUID player, Permission permission) {
        return permission.everyoneByDefault() || (server != null && isOperator(server, player));
    }

    //? if >=1.21.11 {
    private static boolean isAdmin(CommandSourceStack source) {
        return source.permissions().hasPermission(Permissions.COMMANDS_ADMIN);
    }

    // The op list is keyed by UUID alone, so the name can stay empty.
    private static boolean isOperator(MinecraftServer server, UUID player) {
        return server.getPlayerList().isOp(new NameAndId(player, ""));
    }
    //?} else {
    /*private static boolean isAdmin(CommandSourceStack source) {
        return source.hasPermission(Commands.LEVEL_ADMINS);
    }

    // The op list is keyed by UUID alone, so the name can stay empty.
    private static boolean isOperator(MinecraftServer server, UUID player) {
        return server.getPlayerList().isOp(new GameProfile(player, ""));
    }
    *///?}
}
