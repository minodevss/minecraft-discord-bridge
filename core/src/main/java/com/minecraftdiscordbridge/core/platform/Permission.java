package com.minecraftdiscordbridge.core.platform;

public enum Permission {
    LINK("command.link", true),
    TOGGLE("command.toggle", true),
    STATUS("command.status", false),
    RELOAD("command.reload", false),
    SETUP("command.setup", false),
    GATE_BYPASS("gate.bypass", false);

    public static final String NAMESPACE = "minecraftdiscordbridge";

    private final String path;
    private final boolean everyoneByDefault;

    Permission(String path, boolean everyoneByDefault) {
        this.path = path;
        this.everyoneByDefault = everyoneByDefault;
    }

    public String path() {
        return path;
    }

    public String node() {
        return NAMESPACE + "." + path;
    }

    public boolean everyoneByDefault() {
        return everyoneByDefault;
    }
}
