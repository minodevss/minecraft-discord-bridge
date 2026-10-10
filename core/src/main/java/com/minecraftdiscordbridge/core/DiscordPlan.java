package com.minecraftdiscordbridge.core;

import com.minecraftdiscordbridge.core.commands.SlashCommandCatalog;
import com.minecraftdiscordbridge.core.config.BridgeConfig;
import com.minecraftdiscordbridge.core.config.Channels;
import com.minecraftdiscordbridge.core.config.ConfigSnapshot;
import com.minecraftdiscordbridge.core.config.DiscordConfig;
import com.minecraftdiscordbridge.core.discord.Author;
import com.minecraftdiscordbridge.core.discord.CommandSpec;
import com.minecraftdiscordbridge.core.discord.DiscordSettings;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.OptionalLong;
import java.util.Set;

final class DiscordPlan {
    private DiscordPlan() {}

    static DiscordSettings settings(ConfigSnapshot snapshot, Channels channels, OptionalLong console) {
        BridgeConfig config = snapshot.config();
        Set<Long> channelIds = new LinkedHashSet<>();
        Ids.parse(channels.chat()).ifPresent(channelIds::add);
        Ids.parse(channels.events()).ifPresent(channelIds::add);
        console.ifPresent(channelIds::add);
        List<CommandSpec> commands =
                channelIds.isEmpty() ? SlashCommandCatalog.setupOnly() : SlashCommandCatalog.forConfig(config);
        return new DiscordSettings(
                snapshot.secrets().discordToken(),
                guild(config.discord(), channels),
                channelIds,
                Author.server(),
                config.discord().autoEnableMessageContent(),
                needsMembers(config),
                false,
                false,
                commands);
    }

    static boolean needsMembers(BridgeConfig config) {
        return !config.roleSync().rules().isEmpty()
                || config.linking().requiredRole().isPresent();
    }

    private static long guild(DiscordConfig config, Channels channels) {
        if (config.chatChannel().isPresent() || config.eventsChannel().isPresent()) {
            return DiscordSettings.NO_GUILD;
        }
        return Ids.parse(channels.guild()).orElse(DiscordSettings.NO_GUILD);
    }
}
