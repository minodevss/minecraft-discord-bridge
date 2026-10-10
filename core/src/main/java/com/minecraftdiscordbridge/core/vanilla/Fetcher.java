package com.minecraftdiscordbridge.core.vanilla;

import java.io.IOException;
import java.net.URI;

public interface Fetcher {
    byte[] fetch(URI uri) throws IOException;

    default void close() {}
}
