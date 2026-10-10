package com.minecraftdiscordbridge.core.status;

import com.minecraftdiscordbridge.core.config.PlayerCountConfig;
import com.minecraftdiscordbridge.core.discord.Discord;
import com.minecraftdiscordbridge.core.discord.DiscordId;
import com.minecraftdiscordbridge.core.i18n.Lang;
import java.util.Optional;

public final class StatusBoard {
    private final PlayerCountConfig config;
    private final Optional<DiscordId> topicChannel;
    private final Lang lang;
    private final Discord discord;
    private final Roster roster;

    public StatusBoard(
            PlayerCountConfig config, Optional<DiscordId> topicChannel, Lang lang, Discord discord, Roster roster) {
        this.config = config;
        this.topicChannel = topicChannel;
        this.lang = lang;
        this.discord = discord;
        this.roster = roster;
    }

    public void refresh() {
        String online = lang.count("discord.status.online", roster.visibleCount());
        if (config.botStatus()) {
            discord.updatePresence(online);
        }
        if (config.channelTopic() && topicChannel.isPresent()) {
            discord.updateTopic(topicChannel.get(), online);
        }
    }
}
