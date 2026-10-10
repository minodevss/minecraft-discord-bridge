package com.minecraftdiscordbridge.modded.common;

import com.minecraftdiscordbridge.core.platform.Permission;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public interface LoaderHooks {
    String MOD_ID = "minecraftdiscordbridge";

    String software();

    Path dataDirectory();

    boolean isFakePlayer(ServerPlayer player);

    boolean hasPermission(CommandSourceStack source, Permission permission);

    CompletableFuture<Boolean> hasOfflinePermission(MinecraftServer server, UUID player, Permission permission);
}
