package com.minecraftdiscordbridge.core.platform;

public enum Permission {
    LINK("minecraftdiscordbridge.command.link", true),
    TOGGLE("minecraftdiscordbridge.command.toggle", true),
    STATUS("minecraftdiscordbridge.command.status", false),
    RELOAD("minecraftdiscordbridge.command.reload", false),
    SETUP("minecraftdiscordbridge.command.setup", false);

    private final String node;
    private final boolean everyoneByDefault;

    Permission(String node, boolean everyoneByDefault) {
        this.node = node;
        this.everyoneByDefault = everyoneByDefault;
    }

    public String node() {
        return node;
    }

    public boolean everyoneByDefault() {
        return everyoneByDefault;
    }
}
