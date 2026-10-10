package com.minecraftdiscordbridge.paper;

import com.destroystokyo.paper.profile.ProfileProperty;
import com.minecraftdiscordbridge.core.platform.PlayerProfile;
import com.minecraftdiscordbridge.core.platform.SkinTextures;
import java.util.UUID;
import java.util.function.Predicate;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

final class PaperProfiles {
    private final Predicate<UUID> bedrock;

    PaperProfiles(Predicate<UUID> bedrock) {
        this.bedrock = bedrock;
    }

    PlayerProfile atLogin(UUID id, String name) {
        return new PlayerProfile(id, name, name, null, bedrock.test(id));
    }

    PlayerProfile of(Player player) {
        return new PlayerProfile(
                player.getUniqueId(),
                player.getName(),
                PlainTextComponentSerializer.plainText().serialize(player.displayName()),
                skinTexture(player),
                bedrock.test(player.getUniqueId()));
    }

    private static @Nullable String skinTexture(Player player) {
        for (ProfileProperty property : player.getPlayerProfile().getProperties()) {
            if (SkinTextures.PROPERTY.equals(property.getName())) {
                return SkinTextures.id(property.getValue());
            }
        }
        return null;
    }
}
