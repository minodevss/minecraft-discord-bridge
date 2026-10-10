package com.minecraftdiscordbridge.paper;

import com.minecraftdiscordbridge.core.Bridge;
import com.minecraftdiscordbridge.core.platform.GameCommand;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

final class PaperCommand {
    static final String DESCRIPTION = "Link your Discord account and manage the Discord bridge";

    private PaperCommand() {}

    static LiteralCommandNode<CommandSourceStack> node(Bridge bridge) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(GameCommand.ROOT)
                .executes(context -> {
                    bridge.help(source(context.getSource()));
                    return Command.SINGLE_SUCCESS;
                });
        for (GameCommand command : GameCommand.values()) {
            root.then(Commands.literal(command.literal())
                    .requires(stack -> bridge.canUse(source(stack), command))
                    .executes(context -> {
                        bridge.command(source(context.getSource()), command);
                        return Command.SINGLE_SUCCESS;
                    }));
        }
        return root.build();
    }

    private static PaperCommandSource source(CommandSourceStack stack) {
        return new PaperCommandSource(stack.getSender());
    }
}
