package com.minecraftdiscordbridge.core.storage;

public enum Edition {
    JAVA,
    BEDROCK;

    String id() {
        return EnumIds.id(this);
    }

    static Edition fromId(String id) {
        return EnumIds.fromId(Edition.class, id, "edition");
    }
}
