package com.minecraftdiscordbridge.core.storage;

import java.io.IOException;

public final class ReadOnlyStoreException extends IOException {
    private static final long serialVersionUID = 1L;

    ReadOnlyStoreException(String message) {
        super(message);
    }
}
