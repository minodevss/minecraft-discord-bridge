package com.minecraftdiscordbridge.core.discord;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public record DiscordStatus(
        State state, List<StatusReason> reasons, long gatewayPingMillis, Map<DiscordId, ChannelStatus> channels) {
    public DiscordStatus {
        reasons = List.copyOf(reasons);
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

    public record ChannelStatus(int queued, int inFlight, long dropped, long rateLimited, Optional<String> name) {
        public static final ChannelStatus IDLE = new ChannelStatus(0, 0, 0, 0);

        public ChannelStatus(int queued, int inFlight, long dropped, long rateLimited) {
            this(queued, inFlight, dropped, rateLimited, Optional.empty());
        }

        public ChannelStatus withRateLimited(long count) {
            return new ChannelStatus(queued, inFlight, dropped, count, name);
        }

        public ChannelStatus withName(Optional<String> channelName) {
            return new ChannelStatus(queued, inFlight, dropped, rateLimited, channelName);
        }
    }
}
