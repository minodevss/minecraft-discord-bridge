package com.minecraftdiscordbridge.velocity;

import com.minecraftdiscordbridge.core.Bridge;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;

final class VelocityCommand {
    private static final String ARGUMENTS = "arguments";

    private VelocityCommand() {}

    static BrigadierCommand create(Bridge bridge) {
        return new BrigadierCommand(BrigadierCommand.literalArgumentBuilder("discord")
                .executes(context -> run(bridge, context.getSource(), ""))
                .then(BrigadierCommand.requiredArgumentBuilder(ARGUMENTS, StringArgumentType.greedyString())
                        .executes(context ->
                                run(bridge, context.getSource(), StringArgumentType.getString(context, ARGUMENTS)))));
    }

    private static int run(Bridge bridge, CommandSource source, String input) {
        bridge.command(new VelocityCommandSource(source), CommandArguments.split(input));
        return Command.SINGLE_SUCCESS;
    }
}
