package com.minecraftdiscordbridge.core.config;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

public record ChatConfig(
        boolean minecraftToDiscord,
        boolean discordToMinecraft,
        String format,
        String avatarUrl,
        String avatarUrlFallback,
        boolean pingLinkedPlayers,
        List<Pattern> blockedWords) {
    public ChatConfig {
        blockedWords = List.copyOf(blockedWords);
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return other instanceof ChatConfig that
                && minecraftToDiscord == that.minecraftToDiscord
                && discordToMinecraft == that.discordToMinecraft
                && format.equals(that.format)
                && avatarUrl.equals(that.avatarUrl)
                && avatarUrlFallback.equals(that.avatarUrlFallback)
                && pingLinkedPlayers == that.pingLinkedPlayers
                && sources(blockedWords).equals(sources(that.blockedWords));
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                minecraftToDiscord,
                discordToMinecraft,
                format,
                avatarUrl,
                avatarUrlFallback,
                pingLinkedPlayers,
                sources(blockedWords));
    }

    private static List<String> sources(List<Pattern> patterns) {
        return patterns.stream().map(Pattern::pattern).toList();
    }
}
