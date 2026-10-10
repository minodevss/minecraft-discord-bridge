package com.minecraftdiscordbridge.core.access;

import com.minecraftdiscordbridge.core.discord.DiscordId;
import com.minecraftdiscordbridge.core.discord.MemberLookup;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AccessMemory {
    public static final Duration ROLE_TTL = Duration.ofMinutes(1);
    public static final Duration ALLOWED_GRACE = Duration.ofHours(24);
    private static final int PRUNE_ABOVE = 512;

    private record Seen(MemberLookup lookup, Instant at) {}

    private final Map<DiscordId, Seen> members = new ConcurrentHashMap<>();
    private final Map<UUID, Instant> allowed = new ConcurrentHashMap<>();

    public Optional<MemberLookup> freshMember(DiscordId userId, Instant now) {
        Seen seen = members.get(userId);
        return seen == null || seen.at().plus(ROLE_TTL).isBefore(now) ? Optional.empty() : Optional.of(seen.lookup());
    }

    public void remember(MemberLookup lookup, Instant now) {
        DiscordId userId;
        if (lookup instanceof MemberLookup.Member member) {
            userId = member.userId();
        } else if (lookup instanceof MemberLookup.NotMember notMember) {
            userId = notMember.userId();
        } else {
            return;
        }
        if (members.size() > PRUNE_ABOVE) {
            members.values().removeIf(seen -> seen.at().plus(ROLE_TTL).isBefore(now));
        }
        members.put(userId, new Seen(lookup, now));
    }

    public void allowed(UUID player, Instant now) {
        if (allowed.size() > PRUNE_ABOVE) {
            allowed.values().removeIf(at -> at.plus(ALLOWED_GRACE).isBefore(now));
        }
        allowed.put(player, now);
    }

    public boolean allowedRecently(UUID player, Instant now) {
        Instant at = allowed.get(player);
        return at != null && !at.plus(ALLOWED_GRACE).isBefore(now);
    }

    public void forget(UUID player, DiscordId userId) {
        allowed.remove(player);
        members.remove(userId);
    }
}
