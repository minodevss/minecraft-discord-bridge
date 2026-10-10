package com.minecraftdiscordbridge.core.platform;

public record PlatformInfo(
        String software, String minecraftVersion, int dataVersion, boolean onlineMode, boolean secureForwarding) {
    public PlatformInfo(String software, String minecraftVersion, int dataVersion, boolean onlineMode) {
        this(software, minecraftVersion, dataVersion, onlineMode, false);
    }

    public boolean verifiesPlayers() {
        return onlineMode || secureForwarding;
    }
}
