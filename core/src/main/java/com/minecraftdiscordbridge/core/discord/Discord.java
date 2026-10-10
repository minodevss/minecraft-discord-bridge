package com.minecraftdiscordbridge.core.discord;

import java.util.List;

public interface Discord {
    void start();

    DiscordStatus status();

    void send(OutboundMessage message);

    void updatePresence(String text);

    void updateTopic(long channelId, String topic);

    boolean isApplicationOwner(long userId);

    void shutdown(List<OutboundMessage> finalMessages);
}
