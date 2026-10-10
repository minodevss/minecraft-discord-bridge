package com.minecraftdiscordbridge.modded.fabric;

import com.minecraftdiscordbridge.core.platform.Permission;
import com.minecraftdiscordbridge.modded.common.DefaultPermissions;
import com.minecraftdiscordbridge.modded.common.LoaderHooks;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

final class FabricLoaderHooks implements LoaderHooks {
    @Override
    public String software() {
        String version = FabricLoader.getInstance()
                .getModContainer("fabricloader")
                .map(loader -> loader.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
        return "Fabric " + version;
    }

    @Override
    public Path dataDirectory() {
        return FabricLoader.getInstance().getConfigDir().resolve(LoaderHooks.MOD_ID);
    }

    @Override
    public boolean isFakePlayer(ServerPlayer player) {
        return player instanceof FakePlayer;
    }

    @Override
    public boolean hasPermission(CommandSourceStack source, Permission permission) {
        return Permissions.check(source, permission.node(), DefaultPermissions.allows(source, permission));
    }

    @Override
    public CompletableFuture<Boolean> hasOfflinePermission(MinecraftServer server, UUID player, Permission permission) {
        return Permissions.check(
                player, permission.node(), DefaultPermissions.allowsOffline(server, player, permission));
    }
}
