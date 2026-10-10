package com.minecraftdiscordbridge.core.storage;

import static com.minecraftdiscordbridge.core.log.Log.LOGGER;

import com.minecraftdiscordbridge.core.log.Failures;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ShutdownMarker {
    private final Path file;

    public ShutdownMarker(Path dataDirectory) {
        this.file = dataDirectory.resolve("data").resolve("running");
    }

    public boolean markRunning() {
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
                    Failures.describe(failure));
        }
        return leftOver;
    }

    public void clear() throws IOException {
        Files.deleteIfExists(file);
    }
}
