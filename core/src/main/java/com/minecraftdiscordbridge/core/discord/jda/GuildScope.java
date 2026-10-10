package com.minecraftdiscordbridge.core.discord.jda;

import com.minecraftdiscordbridge.core.discord.DiscordId;
import com.minecraftdiscordbridge.core.discord.DiscordSettings;
import java.util.Optional;
import java.util.Set;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import org.jspecify.annotations.Nullable;

final class GuildScope {
    private final Optional<DiscordId> configured;
    private final Set<DiscordId> channelIds;
    private volatile @Nullable DiscordId resolved;

    GuildScope(DiscordSettings settings) {
        this.configured = settings.guild();
        this.channelIds = settings.channelIds();
        this.resolved = configured.orElse(null);
    }

    boolean waitingForSetup() {
        return configured.isEmpty() && channelIds.isEmpty();
    }

    boolean accepts(long guildId) {
        DiscordId current = resolved;
        return current == null ? waitingForSetup() : current.value() == guildId;
    }

    boolean mayResolveTo(long guildId) {
        DiscordId current = resolved;
        return waitingForSetup() || current == null || current.value() == guildId;
    }

    @Nullable
    Guild current(JDA jda) {
        DiscordId current = resolved;
        return current == null ? null : jda.getGuildById(current.value());
    }

    @Nullable
    Guild resolve(JDA jda) {
        if (configured.isPresent()) {
            return jda.getGuildById(configured.get().value());
        }
        for (DiscordId channelId : channelIds) {
            GuildChannel channel = jda.getGuildChannelById(channelId.value());
            if (channel != null) {
                Guild guild = channel.getGuild();
                resolved = new DiscordId(guild.getIdLong());
                return guild;
            }
        }
        return null;
    }
}
