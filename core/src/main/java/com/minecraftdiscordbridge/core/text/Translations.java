package com.minecraftdiscordbridge.core.text;

import org.jspecify.annotations.Nullable;

@FunctionalInterface
public interface Translations {
    Translations NONE = key -> null;

    @Nullable
    String pattern(String key);
}
