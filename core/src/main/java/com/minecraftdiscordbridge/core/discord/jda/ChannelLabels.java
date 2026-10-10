package com.minecraftdiscordbridge.core.discord.jda;

import com.minecraftdiscordbridge.core.discord.DiscordId;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import org.jspecify.annotations.Nullable;

final class ChannelLabels {
    private ChannelLabels() {}

    static String of(GuildChannel channel) {
        return "#" + channel.getName() + " (" + channel.getId() + ")";
    }

    static String of(@Nullable JDA jda, DiscordId channelId) {
        GuildChannel channel = jda == null ? null : jda.getGuildChannelById(channelId.value());
        return channel == null ? channelId.toString() : of(channel);
    }
}
