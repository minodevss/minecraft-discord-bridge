package com.minecraftdiscordbridge.modded.common;

import com.minecraftdiscordbridge.core.platform.Permission;
import java.nio.file.Path;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

public interface LoaderHooks {
    String software();

    Path dataDirectory();

    boolean isFakePlayer(ServerPlayer player);

    boolean hasPermission(CommandSourceStack source, Permission permission);
}
