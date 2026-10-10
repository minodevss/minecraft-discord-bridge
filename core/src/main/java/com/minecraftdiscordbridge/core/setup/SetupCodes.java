package com.minecraftdiscordbridge.core.setup;

import com.minecraftdiscordbridge.core.linking.Codes;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public final class SetupCodes {
    public static final Duration LIFETIME = Duration.ofMinutes(10);

    private final Codes codes;
    private @Nullable String code;
    private Instant expires = Instant.MIN;

    public SetupCodes(Codes codes) {
        this.codes = codes;
    }

    public String issue(Instant now) {
        String issued = codes.next();
        code = issued;
        expires = now.plus(LIFETIME);
        return issued;
    }

    public Optional<String> active(Instant now) {
        String current = code;
        return current != null && now.isBefore(expires) ? Optional.of(current) : Optional.empty();
    }

    public boolean redeem(String typed, Instant now) {
        String current = code;
        if (current == null || !now.isBefore(expires) || !current.equals(Codes.normalize(typed))) {
            return false;
        }
        code = null;
        return true;
    }
}
