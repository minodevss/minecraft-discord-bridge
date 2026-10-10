package com.minecraftdiscordbridge.core.discord.jda;

import static com.minecraftdiscordbridge.core.log.Log.LOGGER;

import com.minecraftdiscordbridge.core.discord.CommandHashRepository;
import com.minecraftdiscordbridge.core.discord.CommandSpec;
import com.minecraftdiscordbridge.core.discord.DiscordId;
import com.minecraftdiscordbridge.core.log.Failures;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.IntegrationType;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

final class CommandRegistrar {
    private final List<SlashCommandData> data;
    private final CommandHashRepository hashes;

    CommandRegistrar(List<CommandSpec> specs, CommandHashRepository hashes) {
        this.data = specs.stream().map(CommandRegistrar::toData).toList();
        this.hashes = hashes;
    }

    void registerIfChanged(Guild guild, long applicationId) {
        String hash = hash(applicationId);
        var unused = hashes.find(new DiscordId(guild.getIdLong()))
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
                            var unused = hashes.save(new DiscordId(guild.getIdLong()), hash);
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
                .setDefaultPermissions(defaultPermissions(spec.access()));
        spec.koreanDescription().ifPresent(korean -> command.setDescriptionLocalization(DiscordLocale.KOREAN, korean));
        return command.addOptions(
                        spec.options().stream().map(CommandRegistrar::toData).toList())
                .addSubcommands(spec.subcommands().stream()
                        .map(CommandRegistrar::toData)
                        .toList());
    }

    private static SubcommandData toData(CommandSpec.Subcommand spec) {
        SubcommandData subcommand = new SubcommandData(spec.name(), spec.description());
        spec.koreanDescription()
                .ifPresent(korean -> subcommand.setDescriptionLocalization(DiscordLocale.KOREAN, korean));
        return subcommand.addOptions(
                spec.options().stream().map(CommandRegistrar::toData).toList());
    }

    private static OptionData toData(CommandSpec.Option spec) {
        OptionData option = new OptionData(
                        type(spec.type()), spec.name(), spec.description(), spec.required(), spec.autocomplete())
                .addChoices(
                        spec.choices().stream().map(CommandRegistrar::toData).toList());
        spec.koreanDescription().ifPresent(korean -> option.setDescriptionLocalization(DiscordLocale.KOREAN, korean));
        return option;
    }

    private static Command.Choice toData(CommandSpec.Choice spec) {
        Command.Choice choice = new Command.Choice(spec.name(), spec.value());
        spec.koreanName().ifPresent(korean -> choice.setNameLocalization(DiscordLocale.KOREAN, korean));
        return choice;
    }

    private static DefaultMemberPermissions defaultPermissions(CommandSpec.Access access) {
        return switch (access) {
            case EVERYONE -> DefaultMemberPermissions.ENABLED;
            case MANAGE_SERVER -> DefaultMemberPermissions.enabledFor(Permission.MANAGE_SERVER);
            case ADMINS -> DefaultMemberPermissions.DISABLED;
        };
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
