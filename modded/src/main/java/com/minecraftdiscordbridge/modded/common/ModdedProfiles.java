package com.minecraftdiscordbridge.modded.common;

import com.minecraftdiscordbridge.core.platform.PlayerProfile;
import com.minecraftdiscordbridge.core.platform.SkinTextures;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import java.util.Collection;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

final class ModdedProfiles {
    private ModdedProfiles() {}

    static PlayerProfile of(ServerPlayer player) {
        return new PlayerProfile(
                player.getUUID(),
                player.getScoreboardName(),
                player.getDisplayName().getString(),
                skinTexture(player.getGameProfile()),
                false);
    }

    static PlayerProfile of(GameProfile profile) {
        String name = name(profile);
        return new PlayerProfile(id(profile), name, name, skinTexture(profile), false);
    }

    private static @Nullable String skinTexture(GameProfile profile) {
        for (Property property : textures(profile)) {
            String texture = SkinTextures.id(value(property));
            if (texture != null) {
                return texture;
            }
        }
        return null;
    }

    //? if >=1.21.11 {
    private static Collection<Property> textures(GameProfile profile) {
        return profile.properties().get(SkinTextures.PROPERTY);
    }

    private static UUID id(GameProfile profile) {
        return profile.id();
    }

    private static String name(GameProfile profile) {
        return profile.name();
    }
    //?} else {
    /*private static Collection<Property> textures(GameProfile profile) {
        return profile.getProperties().get(SkinTextures.PROPERTY);
    }

    private static UUID id(GameProfile profile) {
        return profile.getId();
    }

    private static String name(GameProfile profile) {
        return profile.getName();
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
