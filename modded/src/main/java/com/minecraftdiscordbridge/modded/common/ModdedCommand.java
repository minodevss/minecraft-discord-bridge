package com.minecraftdiscordbridge.modded.common;

import com.minecraftdiscordbridge.core.Bridge;
import com.minecraftdiscordbridge.core.platform.GameCommand;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.function.BiConsumer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public final class ModdedCommand {
    private final ModdedEvents events;

    private ModdedCommand(ModdedEvents events) {
        this.events = events;
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, ModdedEvents events) {
        ModdedCommand discord = new ModdedCommand(events);
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(GameCommand.ROOT)
                .requires(stack -> events.session() != null)
                .executes(context -> discord.run(context.getSource(), Bridge::help));
        for (GameCommand command : GameCommand.values()) {
            root.then(Commands.literal(command.literal())
                    .requires(stack -> discord.canUse(stack, command))
                    .executes(context ->
                            discord.run(context.getSource(), (bridge, source) -> bridge.command(source, command))));
        }
        dispatcher.register(root);
    }

    private boolean canUse(CommandSourceStack stack, GameCommand command) {
        ModdedEvents.Session current = events.session();
        return current != null && current.bridge().canUse(source(stack), command);
    }

    private int run(CommandSourceStack stack, BiConsumer<Bridge, ModdedCommandSource> action) {
        ModdedEvents.Session current = events.session();
        if (current != null) {
            action.accept(current.bridge(), source(stack));
        }
        return Command.SINGLE_SUCCESS;
    }

    private ModdedCommandSource source(CommandSourceStack stack) {
        return new ModdedCommandSource(stack, events.loader());
    }
}
