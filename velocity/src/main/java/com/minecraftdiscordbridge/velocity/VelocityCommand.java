package com.minecraftdiscordbridge.velocity;

import com.minecraftdiscordbridge.core.Bridge;
import com.minecraftdiscordbridge.core.platform.GameCommand;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;

final class VelocityCommand {
    private VelocityCommand() {}

    static BrigadierCommand create(Bridge bridge) {
        LiteralArgumentBuilder<CommandSource> root = BrigadierCommand.literalArgumentBuilder(GameCommand.ROOT)
                .executes(context -> {
                    bridge.help(new VelocityCommandSource(context.getSource()));
                    return Command.SINGLE_SUCCESS;
                });
        for (GameCommand command : GameCommand.values()) {
            root.then(BrigadierCommand.literalArgumentBuilder(command.literal())
                    .requires(source -> bridge.canUse(new VelocityCommandSource(source), command))
                    .executes(context -> {
                        bridge.command(new VelocityCommandSource(context.getSource()), command);
                        return Command.SINGLE_SUCCESS;
                    }));
        }
        return new BrigadierCommand(root);
    }
}
