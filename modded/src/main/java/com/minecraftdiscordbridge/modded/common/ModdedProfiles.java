package com.minecraftdiscordbridge.modded.common;

import com.minecraftdiscordbridge.core.platform.PlayerProfile;
import com.minecraftdiscordbridge.core.platform.SkinTextures;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import java.util.Collection;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

final class ModdedProfiles {
    private final Predicate<UUID> bedrock;

    ModdedProfiles(Predicate<UUID> bedrock) {
        this.bedrock = bedrock;
    }

    PlayerProfile of(ServerPlayer player) {
        return new PlayerProfile(
                player.getUUID(),
                player.getScoreboardName(),
                player.getDisplayName().getString(),
                skinTexture(player.getGameProfile()),
                bedrock.test(player.getUUID()));
    }

    PlayerProfile of(GameProfile profile) {
        String name = name(profile);
        UUID id = id(profile);
        return new PlayerProfile(id, name, name, skinTexture(profile), bedrock.test(id));
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
