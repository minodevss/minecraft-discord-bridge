package com.minecraftdiscordbridge.modded.common;

import com.minecraftdiscordbridge.core.platform.GameCommand;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public final class ModdedCommand {
    private ModdedCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, ModdedEvents events) {
        LiteralArgumentBuilder<CommandSourceStack> root =
                Commands.literal(GameCommand.ROOT).executes(context -> events.help(context.getSource()));
        for (GameCommand command : GameCommand.values()) {
            root.then(Commands.literal(command.literal())
                    .requires(source -> events.canUse(source, command))
                    .executes(context -> events.command(context.getSource(), command)));
        }
        dispatcher.register(root);
    }
}
