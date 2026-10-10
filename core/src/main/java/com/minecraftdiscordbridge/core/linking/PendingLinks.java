package com.minecraftdiscordbridge.core.linking;

import com.minecraftdiscordbridge.core.platform.CommandSource;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class PendingLinks {
    public static final Duration LIFETIME = Duration.ofMinutes(5);

    private final Codes codes;
    private final Map<String, Pending> byCode = new HashMap<>();
    private final Map<UUID, String> codeByPlayer = new HashMap<>();

    public PendingLinks(Codes codes) {
        this.codes = codes;
    }

    public record Pending(UUID playerId, String playerName, boolean bedrock, CommandSource source, Instant expires) {}

    public String issue(UUID playerId, String playerName, boolean bedrock, CommandSource source, Instant now) {
        forgetExpired(now);
        String previous = codeByPlayer.remove(playerId);
        if (previous != null) {
            byCode.remove(previous);
        }
        String code = codes.next();
        while (byCode.containsKey(code)) {
            code = codes.next();
        }
        byCode.put(code, new Pending(playerId, playerName, bedrock, source, now.plus(LIFETIME)));
        codeByPlayer.put(playerId, code);
        return code;
    }

    public Optional<Pending> redeem(String typed, Instant now) {
        forgetExpired(now);
        Pending pending = byCode.remove(Codes.normalize(typed));
        if (pending != null) {
            codeByPlayer.remove(pending.playerId());
        }
        return Optional.ofNullable(pending);
    }

    private void forgetExpired(Instant now) {
        byCode.values().removeIf(pending -> {
            boolean expired = !now.isBefore(pending.expires());
            if (expired) {
                codeByPlayer.remove(pending.playerId());
            }
            return expired;
        });
    }
}
