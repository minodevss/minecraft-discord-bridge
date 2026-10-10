package com.minecraftdiscordbridge.paper;

import com.minecraftdiscordbridge.core.Bridge;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

final class PaperCommand {
    static final String DESCRIPTION = "Link your Discord account and manage the Discord bridge";
    private static final String ARGUMENTS = "arguments";

    private PaperCommand() {}

    static LiteralCommandNode<CommandSourceStack> node(Bridge bridge) {
        return Commands.literal("discord")
                .executes(context -> run(bridge, context.getSource(), ""))
                .then(Commands.argument(ARGUMENTS, StringArgumentType.greedyString())
                        .executes(context ->
                                run(bridge, context.getSource(), StringArgumentType.getString(context, ARGUMENTS))))
                .build();
    }

    private static int run(Bridge bridge, CommandSourceStack source, String input) {
        bridge.command(new PaperCommandSource(source.getSender()), CommandArguments.split(input));
        return Command.SINGLE_SUCCESS;
    }
}
