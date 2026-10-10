package com.minecraftdiscordbridge.modded.common;

import com.minecraftdiscordbridge.core.platform.GameCommand;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public final class DiscordCommand {
    private DiscordCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, ModdedBridge bridge) {
        LiteralArgumentBuilder<CommandSourceStack> root =
                Commands.literal(GameCommand.ROOT).executes(context -> bridge.help(context.getSource()));
        for (GameCommand command : GameCommand.values()) {
            root.then(Commands.literal(command.literal())
                    .requires(source -> bridge.canUse(source, command))
                    .executes(context -> bridge.command(context.getSource(), command)));
        }
        dispatcher.register(root);
    }
}
