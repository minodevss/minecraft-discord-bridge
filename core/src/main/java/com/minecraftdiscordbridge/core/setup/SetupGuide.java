package com.minecraftdiscordbridge.core.setup;

import static com.minecraftdiscordbridge.core.log.Log.LOGGER;

import java.nio.file.Path;

public final class SetupGuide {
    private SetupGuide() {}

    public static final String QUICK_START = "https://github.com/minodevss/minecraft-discord-bridge#quick-start";

    public static void log(Path dataDirectory) {
        LOGGER.warn(
                "No Discord bot token is set, so the bridge is idle. Create a bot at"
                        + " https://discord.com/developers/applications (Bot > Reset Token), put its token in {} and"
                        + " run /discord reload. Setup guide: {}",
                dataDirectory.resolve("secrets.yml"),
                QUICK_START);
    }

    public static void logConfigErrors() {
        LOGGER.error("Fix the problems above and run /discord reload");
    }
}
