package com.minecraftdiscordbridge.core.discord;

import java.util.Map;
import org.jspecify.annotations.Nullable;

public record DiscordStatus(
        State state, @Nullable String reason, long gatewayPingMillis, Map<Long, ChannelStatus> channels) {
    public DiscordStatus {
        channels = Map.copyOf(channels);
    }

    public enum State {
        CONNECTING,
        READY,
        DEGRADED,
        STOPPED
    }

    public record ChannelStatus(int queued, int inFlight, long dropped, long rateLimited) {
        public static final ChannelStatus IDLE = new ChannelStatus(0, 0, 0, 0);

        public ChannelStatus withRateLimited(long count) {
            return new ChannelStatus(queued, inFlight, dropped, count);
        }
    }
}
