package com.minecraftdiscordbridge.paper;

import com.minecraftdiscordbridge.core.platform.PlayerProfile;
import java.util.UUID;
import java.util.function.Predicate;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;

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
                SkinTextures.id(player.getPlayerProfile().getTextures().getSkin()),
                bedrock.test(player.getUniqueId()));
    }
}
