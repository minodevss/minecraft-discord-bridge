package com.minecraftdiscordbridge.core;

import com.minecraftdiscordbridge.core.loop.CoreLoop;
import com.minecraftdiscordbridge.core.platform.CommandSource;
import com.minecraftdiscordbridge.core.platform.GameEvent;
import com.minecraftdiscordbridge.core.platform.Platform;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Bridge {
    private static final Logger LOGGER = LoggerFactory.getLogger("MinecraftDiscordBridge");

    private final Platform platform;
    private final CoreLoop loop;

    private Bridge(Platform platform) {
        this.platform = platform;
        this.loop = new CoreLoop();
    }

    public static Bridge create(Platform platform) {
        return new Bridge(platform);
    }

    public void start() {
        loop.control(() -> LOGGER.info("Starting on {}", platform.info().software()));
    }

    public void publish(GameEvent event) {
        loop.content(() -> LOGGER.debug("Game event {}", event));
    }

    public void command(CommandSource source, List<String> arguments) {
        List<String> copy = List.copyOf(arguments);
        loop.control(() -> LOGGER.debug("Command from {}: {}", source.name(), copy));
    }

    public void stop() {
        loop.close();
    }
}
