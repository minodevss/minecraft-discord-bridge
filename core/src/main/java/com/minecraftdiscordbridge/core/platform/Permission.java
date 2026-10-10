package com.minecraftdiscordbridge.core.platform;

public enum Permission {
    LINK("command.link", true, "Get a code to link your Discord account, or unlink it"),
    TOGGLE("command.toggle", true, "Hide or show messages from Discord for yourself"),
    STATUS("command.status", false, "Show the Discord connection, queues and linked accounts"),
    RELOAD("command.reload", false, "Reload config.yml and secrets.yml"),
    SETUP("command.setup", false, "Get a one-time code for /setup in Discord (console only)"),
    GATE_BYPASS("gate.bypass", false, "Join without the linked Discord account or role the join gate asks for");

    public static final String NAMESPACE = "minecraftdiscordbridge";

    private final String path;
    private final boolean everyoneByDefault;
    private final String description;

    Permission(String path, boolean everyoneByDefault, String description) {
        this.path = path;
        this.everyoneByDefault = everyoneByDefault;
        this.description = description;
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

    public String description() {
        return description;
    }
}
