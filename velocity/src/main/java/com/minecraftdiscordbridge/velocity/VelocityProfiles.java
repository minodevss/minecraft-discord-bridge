package com.minecraftdiscordbridge.velocity;

import com.minecraftdiscordbridge.core.platform.PlayerProfile;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.util.GameProfile;
import java.util.UUID;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

final class VelocityProfiles {
    private static final String TEXTURES = "textures";

    private final Predicate<UUID> bedrock;

    VelocityProfiles(Predicate<UUID> bedrock) {
        this.bedrock = bedrock;
    }

    PlayerProfile of(Player player) {
        return new PlayerProfile(
                player.getUniqueId(),
                player.getUsername(),
                player.getUsername(),
                skinTexture(player),
                bedrock.test(player.getUniqueId()));
    }

    private static @Nullable String skinTexture(Player player) {
        for (GameProfile.Property property : player.getGameProfileProperties()) {
            if (TEXTURES.equals(property.getName())) {
                return SkinTextures.id(property.getValue());
            }
        }
        return null;
    }
}
