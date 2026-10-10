package com.minecraftdiscordbridge.core.config;

import com.minecraftdiscordbridge.core.discord.DiscordId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

public record ChatConfig(
        Optional<DiscordId> channel,
        boolean minecraftToDiscord,
        boolean discordToMinecraft,
        String format,
        String avatarUrl,
        boolean pingLinkedPlayers,
        List<Pattern> blockedWords) {
    public ChatConfig {
        blockedWords = List.copyOf(blockedWords);
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return other instanceof ChatConfig that
                && channel.equals(that.channel)
                && minecraftToDiscord == that.minecraftToDiscord
                && discordToMinecraft == that.discordToMinecraft
                && format.equals(that.format)
                && avatarUrl.equals(that.avatarUrl)
                && pingLinkedPlayers == that.pingLinkedPlayers
                && sources(blockedWords).equals(sources(that.blockedWords));
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                channel,
                minecraftToDiscord,
                discordToMinecraft,
                format,
                avatarUrl,
                pingLinkedPlayers,
                sources(blockedWords));
    }

    private static List<String> sources(List<Pattern> patterns) {
        return patterns.stream().map(Pattern::pattern).toList();
    }
}
