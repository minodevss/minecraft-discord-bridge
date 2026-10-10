package com.minecraftdiscordbridge.modded.neoforge;

import com.minecraftdiscordbridge.core.platform.Permission;
import com.minecraftdiscordbridge.modded.common.DefaultPermissions;
import com.minecraftdiscordbridge.modded.common.LoaderHooks;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;

final class NeoForgeLoaderHooks implements LoaderHooks {
    private final Map<Permission, PermissionNode<Boolean>> nodes = new EnumMap<>(Permission.class);

    NeoForgeLoaderHooks() {
        for (Permission permission : Permission.values()) {
            nodes.put(permission, node(permission));
        }
    }

    void registerNodes(PermissionGatherEvent.Nodes event) {
        event.addNodes(List.<PermissionNode<?>>copyOf(nodes.values()));
    }

    @Override
    public String software() {
        String version = ModList.get()
                .getModContainerById("neoforge")
                .map(neoForge -> neoForge.getModInfo().getVersion().toString())
                .orElse("unknown");
        return "NeoForge " + version;
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
        return PermissionAPI.getPermission(player, Objects.requireNonNull(nodes.get(permission)));
    }

    private static PermissionNode<Boolean> node(Permission permission) {
        return new PermissionNode<>(
                Permission.NAMESPACE,
                permission.path(),
                PermissionTypes.BOOLEAN,
                (player, playerId, context) -> DefaultPermissions.allows(player, permission));
    }
}
