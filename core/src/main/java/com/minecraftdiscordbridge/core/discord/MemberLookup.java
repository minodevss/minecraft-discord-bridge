package com.minecraftdiscordbridge.core.discord;

import java.util.Set;

public sealed interface MemberLookup {
    record Member(long userId, Set<Long> roleIds) implements MemberLookup {
        public Member {
            roleIds = Set.copyOf(roleIds);
        }
    }

    record NotMember(long userId) implements MemberLookup {}

    record Unavailable(String reason) implements MemberLookup {}
}
