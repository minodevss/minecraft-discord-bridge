package com.minecraftdiscordbridge.core.status;

import com.minecraftdiscordbridge.core.config.StatusConfig;
import com.minecraftdiscordbridge.core.discord.Discord;
import com.minecraftdiscordbridge.core.i18n.Lang;
import java.util.OptionalLong;

public final class StatusBoard {
    private final StatusConfig config;
    private final OptionalLong topicChannel;
    private final Lang lang;
    private final Discord discord;
    private final Roster roster;

    public StatusBoard(StatusConfig config, OptionalLong topicChannel, Lang lang, Discord discord, Roster roster) {
        this.config = config;
        this.topicChannel = topicChannel;
        this.lang = lang;
        this.discord = discord;
        this.roster = roster;
    }

    public void refresh() {
        String online = lang.count("discord.status.online", roster.visibleCount());
        if (config.presence()) {
            discord.updatePresence(online);
        }
        if (config.topic() && topicChannel.isPresent()) {
            discord.updateTopic(topicChannel.getAsLong(), online);
        }
    }
}
