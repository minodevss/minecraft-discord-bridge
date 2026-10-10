package com.minecraftdiscordbridge.paper;

import com.minecraftdiscordbridge.core.platform.CommandSource;
import com.minecraftdiscordbridge.core.platform.Permission;
import java.util.UUID;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

record PaperCommandSource(CommandSender sender) implements CommandSource {
    @Override
    public @Nullable UUID playerId() {
        return sender instanceof Player player ? player.getUniqueId() : null;
    }

    @Override
    public String name() {
        return sender.getName();
    }

    @Override
    public boolean hasPermission(Permission permission) {
        return sender.hasPermission(permission.node());
    }

    @Override
    public void reply(String json) {
        sender.sendMessage(GsonComponentSerializer.gson().deserialize(json));
    }
}
