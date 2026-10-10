package com.minecraftdiscordbridge.core.chat;

import com.minecraftdiscordbridge.core.config.ChatConfig;
import com.minecraftdiscordbridge.core.platform.PlayerProfile;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.jspecify.annotations.Nullable;

final class Avatars {
    private static final String TEXTURE = "{texture}";

    private Avatars() {}

    static @Nullable String url(ChatConfig chat, PlayerProfile player) {
        String skin = player.skinTexture();
        String texture = skin == null ? "" : skin.strip();
        String template =
                chat.avatarUrl().contains(TEXTURE) && texture.isEmpty() ? chat.avatarUrlFallback() : chat.avatarUrl();
        if (template.isBlank()) {
            return null;
        }
        return template.replace(TEXTURE, encode(texture))
                .replace("{uuid}", player.id().toString())
                .replace("{name}", encode(player.name()));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
