package com.minecraftdiscordbridge.core.linking;

import com.minecraftdiscordbridge.core.codes.Codes;
import com.minecraftdiscordbridge.core.platform.CommandSource;
import com.minecraftdiscordbridge.core.storage.Edition;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class PendingLinks {
    public static final Duration LIFETIME = Duration.ofMinutes(5);

    private final Codes codes;
    private final Map<String, Pending> byCode = new HashMap<>();
    private final Map<UUID, String> codeByPlayer = new HashMap<>();
    private final Map<UUID, CommandSource> askedInGame = new HashMap<>();

    public PendingLinks(Codes codes) {
        this.codes = codes;
    }

    public record Pending(UUID playerId, String playerName, Edition edition, Instant expires) {}

    public String issue(UUID playerId, String playerName, Edition edition, Instant now) {
        forgetExpired(now);
        forget(playerId);
        String code = codes.next();
        while (byCode.containsKey(code)) {
            code = codes.next();
        }
        byCode.put(code, new Pending(playerId, playerName, edition, now.plus(LIFETIME)));
        codeByPlayer.put(playerId, code);
        return code;
    }

    public String issueInGame(CommandSource player, UUID playerId, Edition edition, Instant now) {
        String code = issue(playerId, player.name(), edition, now);
        askedInGame.put(playerId, player);
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

    public Optional<CommandSource> takeAskedInGame(UUID playerId) {
        return Optional.ofNullable(askedInGame.remove(playerId));
    }

    public Set<String> codes(Instant now) {
        forgetExpired(now);
        return Set.copyOf(byCode.keySet());
    }

    private void forget(UUID playerId) {
        String previous = codeByPlayer.remove(playerId);
        if (previous != null) {
            byCode.remove(previous);
        }
        askedInGame.remove(playerId);
    }

    private void forgetExpired(Instant now) {
        byCode.values().removeIf(pending -> {
            boolean expired = !now.isBefore(pending.expires());
            if (expired) {
                codeByPlayer.remove(pending.playerId());
                askedInGame.remove(pending.playerId());
            }
            return expired;
        });
    }
}
