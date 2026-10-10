package com.minecraftdiscordbridge.core.text;

import java.util.OptionalInt;
import org.jspecify.annotations.Nullable;

public interface MentionResolver {
    MentionResolver NONE = new MentionResolver() {
        @Override
        public @Nullable String userName(long userId) {
            return null;
        }

        @Override
        public @Nullable Role role(long roleId) {
            return null;
        }

        @Override
        public @Nullable String channelName(long channelId) {
            return null;
        }
    };

    @Nullable
    String userName(long userId);

    @Nullable
    Role role(long roleId);

    @Nullable
    String channelName(long channelId);

    record Role(String name, OptionalInt rgb) {
        public static Role uncolored(String name) {
            return new Role(name, OptionalInt.empty());
        }

        public static Role colored(String name, int rgb) {
            return new Role(name, OptionalInt.of(rgb));
        }
    }
}
