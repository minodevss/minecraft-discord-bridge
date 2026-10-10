package com.minecraftdiscordbridge.core;

import java.util.Optional;
import java.util.OptionalLong;

final class Ids {
    private Ids() {}

    static OptionalLong parse(Optional<String> snowflake) {
        if (snowflake.isEmpty()) {
            return OptionalLong.empty();
        }
        try {
            return OptionalLong.of(Long.parseUnsignedLong(snowflake.get()));
        } catch (NumberFormatException invalid) {
            return OptionalLong.empty();
        }
    }
}
