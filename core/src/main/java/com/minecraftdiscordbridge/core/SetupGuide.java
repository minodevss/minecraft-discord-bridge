package com.minecraftdiscordbridge.core;

import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class SetupGuide {
    private static final Logger LOGGER = LoggerFactory.getLogger("MinecraftDiscordBridge");

    private SetupGuide() {}

    static void log(Path dataDirectory) {
        LOGGER.warn("No Discord bot token is set, so the Discord bridge is idle. To connect it:");
        LOGGER.warn("1. Create an application at https://discord.com/developers/applications");
        LOGGER.warn("2. Open Bot, click Reset Token and copy the token");
        LOGGER.warn(
                "3. Paste it into {} or set the MCDB_DISCORD_TOKEN environment variable",
                dataDirectory.resolve("secrets.yml"));
        LOGGER.warn("4. Run /discord reload");
    }

    static void logConfigErrors() {
        LOGGER.error("Fix config.yml and run /discord reload");
    }
}
