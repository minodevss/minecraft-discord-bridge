package com.minecraftdiscordbridge.core.discord;

import java.util.Set;

public sealed interface MemberLookup {
    record Member(DiscordId userId, Set<DiscordId> roleIds) implements MemberLookup {
        public Member {
            roleIds = Set.copyOf(roleIds);
        }
    }

    record NotMember(DiscordId userId) implements MemberLookup {}

    record Unavailable(String reason) implements MemberLookup {}
}
