package com.minecraftdiscordbridge.core;

import com.minecraftdiscordbridge.core.storage.AtomicFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ShutdownMarker {
    private static final Logger LOGGER = LoggerFactory.getLogger("MinecraftDiscordBridge");

    private final Path file;

    ShutdownMarker(Path dataDirectory) {
        this.file = dataDirectory.resolve("data").resolve("running");
    }

    boolean markRunning() {
        boolean leftOver = Files.exists(file);
        try {
            AtomicFile.write(
                    file,
                    "The server is running. This file is deleted when it stops cleanly.\n"
                            .getBytes(StandardCharsets.UTF_8));
        } catch (IOException failure) {
            LOGGER.warn(
                    "Could not write {} ({}); unclean shutdowns will not be reported. Check that the folder is"
                            + " writable",
                    file,
                    failure.getMessage());
        }
        return leftOver;
    }

    void clear() throws IOException {
        Files.deleteIfExists(file);
    }
}
