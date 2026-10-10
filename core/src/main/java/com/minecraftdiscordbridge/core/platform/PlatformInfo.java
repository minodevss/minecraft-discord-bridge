package com.minecraftdiscordbridge.core.platform;

public record PlatformInfo(
        String software,
        String minecraftVersion,
        int dataVersion,
        boolean onlineMode,
        boolean secureForwarding,
        boolean supportsWhitelist) {
    public PlatformInfo(String software, String minecraftVersion, int dataVersion, boolean onlineMode) {
        this(software, minecraftVersion, dataVersion, onlineMode, false);
    }

    public PlatformInfo(
            String software, String minecraftVersion, int dataVersion, boolean onlineMode, boolean secureForwarding) {
        this(software, minecraftVersion, dataVersion, onlineMode, secureForwarding, true);
    }

    public boolean verifiesPlayers() {
        return onlineMode || secureForwarding;
    }
}
