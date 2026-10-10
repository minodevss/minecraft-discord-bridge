package com.minecraftdiscordbridge.core.discord;

public interface InteractionReply {
    void reply(String text);

    void replyEphemeral(String text);

    void defer(boolean ephemeral);
}
