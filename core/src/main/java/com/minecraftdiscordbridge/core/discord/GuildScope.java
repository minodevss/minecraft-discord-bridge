package com.minecraftdiscordbridge.core.discord;

import java.util.Set;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import org.jspecify.annotations.Nullable;

final class GuildScope {
    private final long configured;
    private final Set<Long> channelIds;
    private volatile long resolved;

    GuildScope(DiscordSettings settings) {
        this.configured = settings.guildId();
        this.channelIds = settings.channelIds();
        this.resolved = configured;
    }

    boolean waitingForSetup() {
        return configured == DiscordSettings.NO_GUILD && channelIds.isEmpty();
    }

    boolean accepts(long guildId) {
        long current = resolved;
        return current == DiscordSettings.NO_GUILD ? waitingForSetup() : current == guildId;
    }

    boolean mayResolveTo(long guildId) {
        return waitingForSetup() || resolved == DiscordSettings.NO_GUILD || resolved == guildId;
    }

    @Nullable
    Guild current(JDA jda) {
        long current = resolved;
        return current == DiscordSettings.NO_GUILD ? null : jda.getGuildById(current);
    }

    @Nullable
    Guild resolve(JDA jda) {
        if (configured != DiscordSettings.NO_GUILD) {
            return jda.getGuildById(configured);
        }
        for (long channelId : channelIds) {
            GuildChannel channel = jda.getGuildChannelById(channelId);
            if (channel != null) {
                Guild guild = channel.getGuild();
                resolved = guild.getIdLong();
                return guild;
            }
        }
        return null;
    }
}
