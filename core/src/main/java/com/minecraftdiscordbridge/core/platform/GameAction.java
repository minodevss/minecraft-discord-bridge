package com.minecraftdiscordbridge.core.platform;

public sealed interface GameAction {
    String describe();

    record WhitelistAdd(String playerName) implements GameAction {
        @Override
        public String describe() {
            return "add " + playerName + " to the whitelist";
        }
    }

    record WhitelistRemove(String playerName) implements GameAction {
        @Override
        public String describe() {
            return "remove " + playerName + " from the whitelist";
        }
    }

    record Kick(String playerName, String reason) implements GameAction {
        @Override
        public String describe() {
            return "kick " + playerName;
        }
    }

    record RunConsoleCommand(String command) implements GameAction {
        @Override
        public String describe() {
            return "run the console command " + command;
        }
    }
}
