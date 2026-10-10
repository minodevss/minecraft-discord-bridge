package com.minecraftdiscordbridge.core.storage;

import java.util.Locale;

public enum Edition {
    JAVA,
    BEDROCK;

    String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    static Edition fromId(String id) {
        for (Edition edition : values()) {
            if (edition.id().equals(id)) {
                return edition;
            }
        }
        throw new IllegalArgumentException("unknown edition \"" + id + "\"; expected java or bedrock");
    }
}
