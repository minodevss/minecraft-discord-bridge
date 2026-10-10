package com.minecraftdiscordbridge.core.discord.jda;

import static com.minecraftdiscordbridge.core.log.Log.LOGGER;

import com.minecraftdiscordbridge.core.discord.CommandReply;
import com.minecraftdiscordbridge.core.log.Failures;
import java.util.concurrent.atomic.AtomicReference;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;

final class JdaCommandReply implements CommandReply {
    private enum Stage {
        OPEN,
        DEFERRED,
        ANSWERED
    }

    private final IReplyCallback interaction;
    private final AtomicReference<Stage> stage = new AtomicReference<>(Stage.OPEN);

    JdaCommandReply(IReplyCallback interaction) {
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
            interaction.deferReply(ephemeral).queue(null, JdaCommandReply::logFailure);
        }
    }

    private void answer(String text, boolean ephemeral) {
        if (stage.compareAndSet(Stage.OPEN, Stage.ANSWERED)) {
            interaction.reply(Messages.create(text)).setEphemeral(ephemeral).queue(null, JdaCommandReply::logFailure);
        } else if (stage.compareAndSet(Stage.DEFERRED, Stage.ANSWERED)) {
            interaction.getHook().editOriginal(Messages.edit(text)).queue(null, JdaCommandReply::logFailure);
        } else {
            interaction
                    .getHook()
                    .sendMessage(Messages.create(text))
                    .setEphemeral(ephemeral)
                    .queue(null, JdaCommandReply::logFailure);
        }
    }

    private static void logFailure(Throwable failure) {
        LOGGER.warn("Could not answer a Discord command: {}", Failures.describe(failure));
    }
}
