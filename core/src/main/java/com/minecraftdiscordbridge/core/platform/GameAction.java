package com.minecraftdiscordbridge.core.platform;

public sealed interface GameAction {
    record WhitelistAdd(String playerName) implements GameAction {}

    record WhitelistRemove(String playerName) implements GameAction {}

    record Kick(String playerName, String reason) implements GameAction {}
}
