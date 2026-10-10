package com.minecraftdiscordbridge.modded.common;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public final class DiscordCommand {
    private static final String ARGUMENTS = "arguments";

    private DiscordCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, ModdedBridge bridge) {
        dispatcher.register(Commands.literal("discord")
                .executes(context -> bridge.command(context.getSource(), ""))
                .then(Commands.argument(ARGUMENTS, StringArgumentType.greedyString())
                        .executes(context ->
                                bridge.command(context.getSource(), StringArgumentType.getString(context, ARGUMENTS)))));
    }
}
