package com.minecraftdiscordbridge.core.discord.jda;

import com.minecraftdiscordbridge.core.discord.InteractionReply;
import java.util.concurrent.atomic.AtomicReference;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class JdaInteractionReply implements InteractionReply {
    private static final Logger LOGGER = LoggerFactory.getLogger("MinecraftDiscordBridge");

    private enum Stage {
        OPEN,
        DEFERRED,
        ANSWERED
    }

    private final IReplyCallback interaction;
    private final AtomicReference<Stage> stage = new AtomicReference<>(Stage.OPEN);

    JdaInteractionReply(IReplyCallback interaction) {
        this.interaction = interaction;
    }

    @Override
    public void reply(String text) {
        answer(text, false);
    }

    @Override
    public void replyEphemeral(String text) {
        answer(text, true);
    }

    @Override
    public void defer(boolean ephemeral) {
        if (stage.compareAndSet(Stage.OPEN, Stage.DEFERRED)) {
            interaction.deferReply(ephemeral).queue(null, JdaInteractionReply::logFailure);
        }
    }

    @Override
    public void editReply(String text) {
        if (stage.compareAndSet(Stage.OPEN, Stage.ANSWERED)) {
            interaction.reply(Messages.create(text)).queue(null, JdaInteractionReply::logFailure);
            return;
        }
        stage.set(Stage.ANSWERED);
        interaction.getHook().editOriginal(Messages.edit(text)).queue(null, JdaInteractionReply::logFailure);
    }

    private void answer(String text, boolean ephemeral) {
        if (stage.compareAndSet(Stage.OPEN, Stage.ANSWERED)) {
            interaction
                    .reply(Messages.create(text))
                    .setEphemeral(ephemeral)
                    .queue(null, JdaInteractionReply::logFailure);
        } else if (stage.compareAndSet(Stage.DEFERRED, Stage.ANSWERED)) {
            interaction.getHook().editOriginal(Messages.edit(text)).queue(null, JdaInteractionReply::logFailure);
        } else {
            interaction
                    .getHook()
                    .sendMessage(Messages.create(text))
                    .setEphemeral(ephemeral)
                    .queue(null, JdaInteractionReply::logFailure);
        }
    }

    private static void logFailure(Throwable failure) {
        LOGGER.warn("Could not answer a Discord command: {}", Failures.describe(failure));
    }
}
