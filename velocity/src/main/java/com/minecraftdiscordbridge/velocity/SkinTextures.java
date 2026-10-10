package com.minecraftdiscordbridge.velocity;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.jspecify.annotations.Nullable;

final class SkinTextures {
    private SkinTextures() {}

    static @Nullable String id(String encodedTextures) {
        try {
            String json = new String(Base64.getDecoder().decode(encodedTextures), StandardCharsets.UTF_8);
            JsonObject textures = JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("textures");
            JsonObject skin = textures == null ? null : textures.getAsJsonObject("SKIN");
            JsonElement url = skin == null ? null : skin.get("url");
            return url == null
                    ? null
                    : lastSegment(URI.create(url.getAsString()).getPath());
        } catch (RuntimeException malformed) {
            return null;
        }
    }

    private static @Nullable String lastSegment(@Nullable String path) {
        if (path == null) {
            return null;
        }
        String id = path.substring(path.lastIndexOf('/') + 1);
        return id.isEmpty() ? null : id;
    }
}
