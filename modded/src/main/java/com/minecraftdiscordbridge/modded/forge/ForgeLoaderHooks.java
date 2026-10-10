package com.minecraftdiscordbridge.modded.forge;

import com.minecraftdiscordbridge.core.platform.Permission;
import com.minecraftdiscordbridge.modded.common.DefaultPermissions;
import com.minecraftdiscordbridge.modded.common.LoaderHooks;
import com.minecraftdiscordbridge.modded.common.ModdedBridge;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.server.permission.PermissionAPI;
import net.minecraftforge.server.permission.events.PermissionGatherEvent;
import net.minecraftforge.server.permission.nodes.PermissionNode;
import net.minecraftforge.server.permission.nodes.PermissionTypes;

final class ForgeLoaderHooks implements LoaderHooks {
    private final Map<Permission, PermissionNode<Boolean>> nodes = new EnumMap<>(Permission.class);

    ForgeLoaderHooks() {
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
                .getModContainerById("forge")
                .map(forge -> forge.getModInfo().getVersion().toString())
                .orElse("unknown");
        return "Forge " + version;
    }

    @Override
    public Path dataDirectory() {
        return FMLPaths.CONFIGDIR.get().resolve(ModdedBridge.MOD_ID);
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
        return PermissionAPI.getPermission(player, nodes.get(permission));
    }

    private static PermissionNode<Boolean> node(Permission permission) {
        String name = permission.node().substring(ModdedBridge.MOD_ID.length() + 1);
        return new PermissionNode<>(
                ModdedBridge.MOD_ID,
                name,
                PermissionTypes.BOOLEAN,
                (player, playerId, context) -> DefaultPermissions.allows(player, permission));
    }
}
