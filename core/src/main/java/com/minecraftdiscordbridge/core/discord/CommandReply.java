package com.minecraftdiscordbridge.core.discord;

public interface CommandReply {
    void reply(String text);

    void replyEphemeral(String text);

    void defer(boolean ephemeral);
}
