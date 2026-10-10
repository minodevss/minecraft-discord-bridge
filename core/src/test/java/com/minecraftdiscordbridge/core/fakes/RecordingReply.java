package com.minecraftdiscordbridge.core.fakes;

import com.minecraftdiscordbridge.core.discord.InteractionReply;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public final class RecordingReply implements InteractionReply {
    public record Answer(String text, boolean ephemeral) {}

    public final BlockingQueue<Answer> answers = new LinkedBlockingQueue<>();

    @Override
    public void reply(String text) {
        answers.add(new Answer(text, false));
    }

    @Override
    public void replyEphemeral(String text) {
        answers.add(new Answer(text, true));
    }

    @Override
    public void defer(boolean ephemeral) {}

    public Answer next() {
        return Waits.take(answers);
    }
}
