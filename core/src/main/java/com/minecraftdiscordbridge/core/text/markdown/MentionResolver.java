package com.minecraftdiscordbridge.core.text.markdown;

import java.util.Optional;
import java.util.OptionalInt;

public interface MentionResolver {
    MentionResolver NONE = new MentionResolver() {
        @Override
        public Optional<String> userName(long userId) {
            return Optional.empty();
        }

        @Override
        public Optional<Role> role(long roleId) {
            return Optional.empty();
        }

        @Override
        public Optional<String> channelName(long channelId) {
            return Optional.empty();
        }
    };

    Optional<String> userName(long userId);

    Optional<Role> role(long roleId);

    Optional<String> channelName(long channelId);

    record Role(String name, OptionalInt rgb) {}
}
