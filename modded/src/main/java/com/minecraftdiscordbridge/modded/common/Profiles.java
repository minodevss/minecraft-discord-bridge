package com.minecraftdiscordbridge.modded.common;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.minecraftdiscordbridge.core.platform.PlayerProfile;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collection;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

final class Profiles {
    private static final String TEXTURES = "textures";

    private Profiles() {}

    static PlayerProfile of(ServerPlayer player) {
        return new PlayerProfile(
                player.getUUID(),
                player.getScoreboardName(),
                player.getDisplayName().getString(),
                skinTexture(player.getGameProfile()),
                false);
    }

    private static @Nullable String skinTexture(GameProfile profile) {
        for (Property property : textures(profile)) {
            String texture = textureId(value(property));
            if (texture != null) {
                return texture;
            }
        }
        return null;
    }

    private static @Nullable String textureId(String encoded) {
        try {
            JsonElement decoded = JsonParser.parseString(
                    new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8));
            JsonObject textures = decoded.getAsJsonObject().getAsJsonObject(TEXTURES);
            JsonObject skin = textures == null ? null : textures.getAsJsonObject("SKIN");
            JsonElement url = skin == null ? null : skin.get("url");
            if (url == null) {
                return null;
            }
            String path = url.getAsString();
            String id = path.substring(path.lastIndexOf('/') + 1);
            return id.isEmpty() ? null : id;
        } catch (RuntimeException malformed) {
            return null;
        }
    }

    //? if >=1.21.11 {
    private static Collection<Property> textures(GameProfile profile) {
        return profile.properties().get(TEXTURES);
    }
    //?} else {
    /*private static Collection<Property> textures(GameProfile profile) {
        return profile.getProperties().get(TEXTURES);
    }
    *///?}

    //? if >=1.21 {
    private static String value(Property property) {
        return property.value();
    }
    //?} else {
    /*private static String value(Property property) {
        return property.getValue();
    }
    *///?}
}
