package com.minecraftdiscordbridge.core.text;

import org.jspecify.annotations.Nullable;

@FunctionalInterface
public interface Translations {
    @Nullable
    String pattern(String key);
}
