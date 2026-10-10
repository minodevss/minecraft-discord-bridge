package com.minecraftdiscordbridge.core.text.markdown;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

record FakeMentions(Map<Long, String> users, Map<Long, Role> roles, Map<Long, String> channels)
        implements MentionResolver {
    static final long STEVE = 123456789012345678L;
    static final long ADMIN = 223456789012345678L;
    static final long HELPER = 223456789012345679L;
    static final long GENERAL = 323456789012345678L;

    static FakeMentions standard() {
        return new FakeMentions(
                Map.of(STEVE, "Steve"),
                Map.of(
                        ADMIN,
                        new Role("Admin", OptionalInt.of(0x3498DB)),
                        HELPER,
                        new Role("Helper", OptionalInt.empty())),
                Map.of(GENERAL, "general"));
    }

    @Override
    public Optional<String> userName(long userId) {
        return Optional.ofNullable(users.get(userId));
    }

    @Override
    public Optional<Role> role(long roleId) {
        return Optional.ofNullable(roles.get(roleId));
    }

    @Override
    public Optional<String> channelName(long channelId) {
        return Optional.ofNullable(channels.get(channelId));
    }
}
