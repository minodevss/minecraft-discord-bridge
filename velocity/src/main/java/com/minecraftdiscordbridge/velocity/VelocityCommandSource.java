package com.minecraftdiscordbridge.velocity;

import com.minecraftdiscordbridge.core.platform.CommandSource;
import com.minecraftdiscordbridge.core.platform.Permission;
import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.api.proxy.Player;
import java.util.UUID;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.jspecify.annotations.Nullable;

record VelocityCommandSource(com.velocitypowered.api.command.CommandSource source) implements CommandSource {
    private static final String CONSOLE = "CONSOLE";

    @Override
    public @Nullable UUID playerId() {
        return source instanceof Player player ? player.getUniqueId() : null;
    }

    @Override
    public String name() {
        return source instanceof Player player ? player.getUsername() : CONSOLE;
    }

    @Override
    public boolean hasPermission(Permission permission) {
        Tristate value = source.getPermissionValue(permission.node());
        return value == Tristate.UNDEFINED ? permission.everyoneByDefault() : value.asBoolean();
    }

    @Override
    public void reply(String json) {
        source.sendMessage(GsonComponentSerializer.gson().deserialize(json));
    }
}
