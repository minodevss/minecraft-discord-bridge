package com.minecraftdiscordbridge.core.storage;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

final class EnumIds {
    private EnumIds() {}

    static String id(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }

    static <E extends Enum<E>> Optional<E> find(Class<E> type, String id) {
        return Arrays.stream(type.getEnumConstants())
                .filter(value -> id(value).equals(id))
                .findFirst();
    }

    static <E extends Enum<E>> E fromId(Class<E> type, String id, String what) {
        return find(type, id)
                .orElseThrow(() -> new IllegalArgumentException("unknown " + what + " \"" + id + "\"; expected "
                        + Arrays.stream(type.getEnumConstants())
                                .map(EnumIds::id)
                                .collect(Collectors.joining(" or "))));
    }
}
