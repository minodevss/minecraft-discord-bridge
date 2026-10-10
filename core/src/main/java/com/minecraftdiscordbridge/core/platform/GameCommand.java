package com.minecraftdiscordbridge.core.platform;

public enum GameCommand {
    LINK("link", Permission.LINK, Sender.PLAYER),
    UNLINK("unlink", Permission.LINK, Sender.PLAYER),
    TOGGLE("toggle", Permission.TOGGLE, Sender.PLAYER),
    STATUS("status", Permission.STATUS, Sender.ANYONE),
    RELOAD("reload", Permission.RELOAD, Sender.ANYONE),
    SETUP("setup", Permission.SETUP, Sender.CONSOLE);

    public static final String ROOT = "discord";

    public enum Sender {
        PLAYER,
        CONSOLE,
        ANYONE;

        boolean includes(CommandSource source) {
            return switch (this) {
                case PLAYER -> source.playerId() != null;
                case CONSOLE -> source.playerId() == null;
                case ANYONE -> true;
            };
        }
    }

    private final String literal;
    private final Permission permission;
    private final Sender sender;

    GameCommand(String literal, Permission permission, Sender sender) {
        this.literal = literal;
        this.permission = permission;
        this.sender = sender;
    }

    public String literal() {
        return literal;
    }

    public Permission permission() {
        return permission;
    }

    public Sender sender() {
        return sender;
    }

    public boolean needsLinking() {
        return this == LINK || this == UNLINK;
    }

    public boolean permits(CommandSource source) {
        return sender.includes(source) && source.hasPermission(permission);
    }

    public boolean available(boolean linkingEnabled) {
        return linkingEnabled || !needsLinking();
    }
}
