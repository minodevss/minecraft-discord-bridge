package com.minecraftdiscordbridge.modded.common;

import com.minecraftdiscordbridge.core.platform.CommandSource;
import com.minecraftdiscordbridge.core.platform.Permission;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

record ServerCommandSource(@Nullable UUID playerId, String name, CommandSourceStack stack, LoaderHooks loader)
        implements CommandSource {

    static ServerCommandSource of(CommandSourceStack stack, LoaderHooks loader) {
        ServerPlayer player = stack.getPlayer();
        return new ServerCommandSource(
                player == null ? null : player.getUUID(), stack.getTextName(), stack, loader);
    }

    @Override
    public boolean hasPermission(Permission permission) {
        return loader.hasPermission(stack, permission);
    }

    @Override
    public void reply(String json) {
        MinecraftServer server = stack.getServer();
        ServerThread.run(server, () -> Texts.parse(json, server.registryAccess()).ifPresent(stack::sendSystemMessage));
    }
}
