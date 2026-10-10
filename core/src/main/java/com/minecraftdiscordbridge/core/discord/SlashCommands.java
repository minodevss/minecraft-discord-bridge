package com.minecraftdiscordbridge.core.discord;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.IntegrationType;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class SlashCommands {
    private static final Logger LOGGER = LoggerFactory.getLogger("MinecraftDiscordBridge");

    private final List<SlashCommandData> data;
    private final CommandHashRepository hashes;

    SlashCommands(List<CommandSpec> specs, CommandHashRepository hashes) {
        this.data = specs.stream().map(SlashCommands::toData).toList();
        this.hashes = hashes;
    }

    void registerIfChanged(Guild guild, long applicationId) {
        String hash = hash(applicationId);
        long guildId = guild.getIdLong();
        var unused = hashes.find(guildId)
                .exceptionally(failure -> Optional.empty())
                .thenAccept(stored -> {
                    if (!stored.equals(Optional.of(hash))) {
                        register(guild, hash);
                    }
                });
    }

    String hash(long applicationId) {
        StringBuilder canonical = new StringBuilder(Long.toUnsignedString(applicationId));
        for (SlashCommandData command : data) {
            canonical.append('\n');
            write(command.toData().toMap(), canonical);
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private void register(Guild guild, String hash) {
        guild.updateCommands()
                .addCommands(data)
                .queue(
                        registered -> {
                            LOGGER.info("Registered {} slash commands in {}", registered.size(), guild.getName());
                            var unused = hashes.save(guild.getIdLong(), hash);
                        },
                        failure -> LOGGER.warn(
                                "Could not register slash commands in {}: {}. Re-invite the bot with the"
                                        + " applications.commands scope",
                                guild.getName(),
                                Failures.describe(failure)));
    }

    static SlashCommandData toData(CommandSpec spec) {
        SlashCommandData command = Commands.slash(spec.name(), spec.description())
                .setContexts(InteractionContextType.GUILD)
                .setIntegrationTypes(IntegrationType.GUILD_INSTALL)
                .setDefaultPermissions(
                        spec.adminOnly() ? DefaultMemberPermissions.DISABLED : DefaultMemberPermissions.ENABLED);
        CommandSpec.Localized korean = spec.korean();
        if (korean != null) {
            command.setNameLocalization(DiscordLocale.KOREAN, korean.name())
                    .setDescriptionLocalization(DiscordLocale.KOREAN, korean.description());
        }
        return command.addOptions(
                spec.options().stream().map(SlashCommands::toData).toList());
    }

    private static OptionData toData(CommandSpec.Option spec) {
        OptionData option = new OptionData(
                type(spec.type()), spec.name(), spec.description(), spec.required(), spec.autocomplete());
        CommandSpec.Localized korean = spec.korean();
        if (korean != null) {
            option.setNameLocalization(DiscordLocale.KOREAN, korean.name())
                    .setDescriptionLocalization(DiscordLocale.KOREAN, korean.description());
        }
        return option;
    }

    private static OptionType type(CommandSpec.OptionType type) {
        return switch (type) {
            case STRING -> OptionType.STRING;
            case INTEGER -> OptionType.INTEGER;
            case BOOLEAN -> OptionType.BOOLEAN;
            case USER -> OptionType.USER;
        };
    }

    private static void write(Object value, StringBuilder out) {
        if (value instanceof Map<?, ?> map) {
            TreeMap<String, Object> sorted = new TreeMap<>();
            map.forEach((key, entry) -> sorted.put(String.valueOf(key), entry));
            out.append('{');
            sorted.forEach((key, entry) -> {
                out.append(key).append('=');
                write(entry, out);
                out.append(';');
            });
            out.append('}');
        } else if (value instanceof List<?> list) {
            out.append('[');
            list.forEach(entry -> {
                write(entry, out);
                out.append(',');
            });
            out.append(']');
        } else {
            out.append(value);
        }
    }
}
