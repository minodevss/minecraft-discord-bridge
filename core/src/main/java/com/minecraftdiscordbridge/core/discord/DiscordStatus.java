package com.minecraftdiscordbridge.core.discord;

import java.util.Map;
import java.util.Optional;

public record DiscordStatus(
        State state, Optional<String> reason, long gatewayPingMillis, Map<DiscordId, ChannelStatus> channels) {
    public DiscordStatus {
        channels = Map.copyOf(channels);
    }

    public boolean isConnected() {
        return state == State.READY || state == State.DEGRADED;
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
