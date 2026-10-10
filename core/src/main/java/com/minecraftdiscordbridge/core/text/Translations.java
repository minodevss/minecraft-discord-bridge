package com.minecraftdiscordbridge.core.text;

import java.util.Optional;

@FunctionalInterface
public interface Translations {
    Optional<String> pattern(String key);
}
