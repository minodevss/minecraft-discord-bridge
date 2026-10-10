package com.minecraftdiscordbridge.modded.forge;

import com.minecraftdiscordbridge.core.platform.Permission;
import com.minecraftdiscordbridge.modded.common.DefaultPermissions;
import com.minecraftdiscordbridge.modded.common.LoaderHooks;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.Util;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.server.ServerLifecycleHooks;
import net.minecraftforge.server.permission.PermissionAPI;
import net.minecraftforge.server.permission.events.PermissionGatherEvent;
import net.minecraftforge.server.permission.nodes.PermissionNode;
import net.minecraftforge.server.permission.nodes.PermissionTypes;

final class ForgeLoaderHooks implements LoaderHooks {
    private final Map<Permission, PermissionNode<Boolean>> nodes = new EnumMap<>(Permission.class);

    ForgeLoaderHooks() {
        for (Permission permission : Permission.values()) {
            nodes.put(permission, defaultNode(permission));
        }
    }

    void registerNodes(PermissionGatherEvent.Nodes event) {
        event.addNodes(List.<PermissionNode<?>>copyOf(nodes.values()));
    }

    @Override
    public String software() {
        String version = ModList.get()
                .getModContainerById("forge")
                .map(forge -> forge.getModInfo().getVersion().toString())
                .orElse("unknown");
        return "Forge " + version;
    }

    @Override
    public Path dataDirectory() {
        return FMLPaths.CONFIGDIR.get().resolve(LoaderHooks.MOD_ID);
    }

    @Override
    public boolean isFakePlayer(ServerPlayer player) {
        return player instanceof FakePlayer;
    }

    @Override
    public boolean hasPermission(CommandSourceStack source, Permission permission) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return DefaultPermissions.allows(source, permission);
        }
        return PermissionAPI.getPermission(player, node(permission));
    }

    @Override
    public CompletableFuture<Boolean> hasOfflinePermission(MinecraftServer server, UUID player, Permission permission) {
        PermissionNode<Boolean> node = node(permission);
        return CompletableFuture.supplyAsync(() -> PermissionAPI.getOfflinePermission(player, node), Util.ioPool());
    }

    private PermissionNode<Boolean> node(Permission permission) {
        return Objects.requireNonNull(nodes.get(permission));
    }

    private static PermissionNode<Boolean> defaultNode(Permission permission) {
        return new PermissionNode<>(
                Permission.NAMESPACE,
                permission.path(),
                PermissionTypes.BOOLEAN,
                (player, playerId, context) -> player != null
                        ? DefaultPermissions.allows(player, permission)
                        : DefaultPermissions.allowsOffline(
                                ServerLifecycleHooks.getCurrentServer(), playerId, permission));
    }
}
