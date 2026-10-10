package com.minecraftdiscordbridge.core.chat;

import com.minecraftdiscordbridge.core.config.ChatConfig;
import com.minecraftdiscordbridge.core.platform.PlayerProfile;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

final class Avatars {
    private static final String TEXTURE = "{texture}";

    private Avatars() {}

    static Optional<String> url(ChatConfig chat, PlayerProfile player) {
        String skin = player.skinTexture();
        String texture = skin == null ? "" : skin.strip();
        String template = chat.avatarUrl();
        if (template.isBlank()) {
            return Optional.empty();
        }
        String uuid = player.id().toString();
        return Optional.of(template.replace(TEXTURE, texture.isEmpty() ? uuid : encode(texture))
                .replace("{uuid}", uuid)
                .replace("{name}", encode(player.name())));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
