package com.minecraftdiscordbridge.core;

import com.minecraftdiscordbridge.core.commands.SlashCommandCatalog;
import com.minecraftdiscordbridge.core.config.BridgeConfig;
import com.minecraftdiscordbridge.core.config.Channels;
import com.minecraftdiscordbridge.core.config.ConfigSnapshot;
import com.minecraftdiscordbridge.core.config.DiscordConfig;
import com.minecraftdiscordbridge.core.discord.Author;
import com.minecraftdiscordbridge.core.discord.CommandSpec;
import com.minecraftdiscordbridge.core.discord.DiscordId;
import com.minecraftdiscordbridge.core.discord.DiscordSettings;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

final class DiscordPlan {
    private DiscordPlan() {}

    static DiscordSettings settings(ConfigSnapshot snapshot, Channels channels, Optional<DiscordId> console) {
        BridgeConfig config = snapshot.config();
        Set<Long> channelIds = new LinkedHashSet<>();
        channels.chat().ifPresent(id -> channelIds.add(id.value()));
        channels.events().ifPresent(id -> channelIds.add(id.value()));
        console.ifPresent(id -> channelIds.add(id.value()));
        consoleInput(config).ifPresent(id -> channelIds.add(id.value()));
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

    static Optional<DiscordId> consoleInput(BridgeConfig config) {
        return config.console().inputUsers().isEmpty()
                ? Optional.empty()
                : config.discord().consoleChannel();
    }

    private static long guild(DiscordConfig config, Channels channels) {
        if (config.chatChannel().isPresent() || config.eventsChannel().isPresent()) {
            return DiscordSettings.NO_GUILD;
        }
        return channels.guild().map(DiscordId::value).orElse(DiscordSettings.NO_GUILD);
    }
}
