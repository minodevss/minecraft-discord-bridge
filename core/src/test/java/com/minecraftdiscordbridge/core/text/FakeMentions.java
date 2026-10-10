package com.minecraftdiscordbridge.core.text;

import java.util.Map;
import org.jspecify.annotations.Nullable;

record FakeMentions(Map<Long, String> users, Map<Long, Role> roles, Map<Long, String> channels)
        implements MentionResolver {
    static final long STEVE = 123456789012345678L;
    static final long ADMIN = 223456789012345678L;
    static final long HELPER = 223456789012345679L;
    static final long GENERAL = 323456789012345678L;

    static FakeMentions standard() {
        return new FakeMentions(
                Map.of(STEVE, "Steve"),
                Map.of(ADMIN, Role.colored("Admin", 0x3498DB), HELPER, Role.uncolored("Helper")),
                Map.of(GENERAL, "general"));
    }

    @Override
    public @Nullable String userName(long userId) {
        return users.get(userId);
    }

    @Override
    public @Nullable Role role(long roleId) {
        return roles.get(roleId);
    }

    @Override
    public @Nullable String channelName(long channelId) {
        return channels.get(channelId);
    }
}
