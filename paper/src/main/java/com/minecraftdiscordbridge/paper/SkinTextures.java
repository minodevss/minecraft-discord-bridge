package com.minecraftdiscordbridge.paper;

import java.net.URL;
import org.jspecify.annotations.Nullable;

final class SkinTextures {
    private SkinTextures() {}

    static @Nullable String id(@Nullable URL skin) {
        if (skin == null) {
            return null;
        }
        String path = skin.getPath();
        String id = path.substring(path.lastIndexOf('/') + 1);
        return id.isEmpty() ? null : id;
    }
}
