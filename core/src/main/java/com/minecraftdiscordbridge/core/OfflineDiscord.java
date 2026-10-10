package com.minecraftdiscordbridge.core;

import com.minecraftdiscordbridge.core.discord.Discord;
import com.minecraftdiscordbridge.core.discord.DiscordStatus;
import com.minecraftdiscordbridge.core.discord.OutboundMessage;
import java.util.List;
import java.util.Map;

final class OfflineDiscord implements Discord {
    static final String STARTING = "The bridge is starting";
    static final String CONNECTING = "Connecting to Discord";
    static final String NO_TOKEN = "No bot token is set. See the server log for the setup steps";
    static final String CONFIG_ERRORS = "config.yml has errors. Fix them and run /discord reload";
    static final String STOPPED = "The bridge has stopped";

    private final DiscordStatus status;

    OfflineDiscord(DiscordStatus.State state, String reason) {
        this.status = new DiscordStatus(state, reason, -1, Map.of());
    }

    static OfflineDiscord stopped(String reason) {
        return new OfflineDiscord(DiscordStatus.State.STOPPED, reason);
    }

    static OfflineDiscord connecting(String reason) {
        return new OfflineDiscord(DiscordStatus.State.CONNECTING, reason);
    }

    @Override
    public void start() {}

    @Override
    public DiscordStatus status() {
        return status;
    }

    @Override
    public void send(OutboundMessage message) {}

    @Override
    public void updatePresence(String text) {}

    @Override
    public void updateTopic(long channelId, String topic) {}

    @Override
    public boolean isApplicationOwner(long userId) {
        return false;
    }

    @Override
    public void shutdown(List<OutboundMessage> finalMessages) {}
}
