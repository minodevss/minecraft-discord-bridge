package com.minecraftdiscordbridge.core.setup;

import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SetupGuide {
    private static final Logger LOGGER = LoggerFactory.getLogger("MinecraftDiscordBridge");

    private SetupGuide() {}

    public static final String QUICK_START = "https://github.com/minodevss/minecraft-discord-bridge#quick-start";

    public static void log(Path dataDirectory) {
        LOGGER.warn("No Discord bot token is set, so the Discord bridge is idle. Quick start: {}", QUICK_START);
        LOGGER.warn(
                "Create a bot at https://discord.com/developers/applications (Bot > Reset Token), put its token in {}"
                        + " and run /discord reload",
                dataDirectory.resolve("secrets.yml"));
    }

    public static void logConfigErrors() {
        LOGGER.error("Fix the problems above and run /discord reload");
    }
}
