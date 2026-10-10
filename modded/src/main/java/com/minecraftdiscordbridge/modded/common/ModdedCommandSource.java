package com.minecraftdiscordbridge.modded.common;

import com.minecraftdiscordbridge.core.platform.CommandSource;
import com.minecraftdiscordbridge.core.platform.Permission;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

record ModdedCommandSource(CommandSourceStack stack, LoaderHooks loader) implements CommandSource {
    @Override
    public @Nullable UUID playerId() {
        ServerPlayer player = stack.getPlayer();
        return player == null ? null : player.getUUID();
    }

    @Override
    public String name() {
        return stack.getTextName();
    }

    @Override
    public boolean hasPermission(Permission permission) {
        return loader.hasPermission(stack, permission);
    }

    @Override
    public void reply(String json) {
        MinecraftServer server = stack.getServer();
        ServerThread.run(
                server, () -> Texts.parse(json, server.registryAccess()).ifPresent(stack::sendSystemMessage));
    }
}
