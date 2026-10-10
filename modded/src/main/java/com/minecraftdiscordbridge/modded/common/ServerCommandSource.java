package com.minecraftdiscordbridge.modded.common;

import com.minecraftdiscordbridge.core.platform.CommandSource;
import com.minecraftdiscordbridge.core.platform.Permission;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

record ServerCommandSource(@Nullable UUID playerId, String name, Set<Permission> granted, CommandSourceStack stack)
        implements CommandSource {

    static ServerCommandSource of(CommandSourceStack stack, LoaderHooks loader) {
        Set<Permission> granted = EnumSet.noneOf(Permission.class);
        for (Permission permission : Permission.values()) {
            if (loader.hasPermission(stack, permission)) {
                granted.add(permission);
            }
        }
        ServerPlayer player = stack.getPlayer();
        return new ServerCommandSource(
                player == null ? null : player.getUUID(),
                stack.getTextName(),
                Collections.unmodifiableSet(granted),
                stack);
    }

    @Override
    public boolean hasPermission(Permission permission) {
        return granted.contains(permission);
    }

    @Override
    public void reply(String json) {
        MinecraftServer server = stack.getServer();
        ServerThread.run(server, () -> Texts.parse(json, server.registryAccess()).ifPresent(stack::sendSystemMessage));
    }
}
