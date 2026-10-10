package com.minecraftdiscordbridge.core;

import com.minecraftdiscordbridge.core.commands.DiscordCommands;
import com.minecraftdiscordbridge.core.config.BridgeConfig;
import com.minecraftdiscordbridge.core.config.ConfigSnapshot;
import com.minecraftdiscordbridge.core.config.DiscordConfig;
import com.minecraftdiscordbridge.core.discord.Author;
import com.minecraftdiscordbridge.core.discord.CommandSpec;
import com.minecraftdiscordbridge.core.discord.DiscordId;
import com.minecraftdiscordbridge.core.discord.DiscordSettings;
import com.minecraftdiscordbridge.core.setup.Channels;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

final class DiscordPlan {
    private DiscordPlan() {}

    static DiscordSettings settings(ConfigSnapshot snapshot, Channels channels) {
        BridgeConfig config = snapshot.config();
        Set<DiscordId> channelIds = channelIds(config, channels);
        List<CommandSpec> commands =
                channelIds.isEmpty() ? DiscordCommands.setupOnly() : DiscordCommands.forConfig(config);
        return new DiscordSettings(
                snapshot.secrets().discordToken(),
                guild(config.discord(), channels),
                channelIds,
                Author.server(),
                config.discord().autoEnableMessageContent(),
                needsMembers(config),
                commands);
    }

    static boolean needsMembers(BridgeConfig config) {
        return !config.roleSync().rules().isEmpty()
                || config.linking().requiredRole().isPresent();
    }

    static Set<DiscordId> channelIds(BridgeConfig config, Channels channels) {
        Set<DiscordId> ids = new LinkedHashSet<>();
        channels.chat().ifPresent(ids::add);
        channels.events().ifPresent(ids::add);
        if (config.console().mirror() || !config.console().inputUsers().isEmpty()) {
            config.discord().consoleChannel().ifPresent(ids::add);
        }
        return ids;
    }

    private static Optional<DiscordId> guild(DiscordConfig config, Channels channels) {
        if (config.chatChannel().isPresent() || config.eventsChannel().isPresent()) {
            return Optional.empty();
        }
        return channels.guild();
    }
}
