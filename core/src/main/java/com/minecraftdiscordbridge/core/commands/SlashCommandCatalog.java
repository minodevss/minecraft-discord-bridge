package com.minecraftdiscordbridge.core.commands;

import com.minecraftdiscordbridge.core.actions.Actions;
import com.minecraftdiscordbridge.core.config.BridgeConfig;
import com.minecraftdiscordbridge.core.discord.CommandSpec;
import com.minecraftdiscordbridge.core.discord.CommandSpec.Localized;
import com.minecraftdiscordbridge.core.discord.CommandSpec.Option;
import com.minecraftdiscordbridge.core.discord.CommandSpec.OptionType;
import com.minecraftdiscordbridge.core.discord.CommandSpec.Subcommand;
import com.minecraftdiscordbridge.core.i18n.Lang;
import com.minecraftdiscordbridge.core.i18n.Language;
import com.minecraftdiscordbridge.core.linking.Linking;
import com.minecraftdiscordbridge.core.setup.ChannelSetup;
import java.util.ArrayList;
import java.util.List;

public final class SlashCommandCatalog {
    public static final String SETUP = "setup";
    public static final String LIST = "list";
    public static final String LINK = "link";
    public static final String UNLINK = "unlink";
    public static final String WHITELIST = "whitelist";
    public static final String KICK = "kick";

    private static final Lang ENGLISH = Lang.of(Language.EN);
    private static final Lang KOREAN = Lang.of(Language.KO);

    private SlashCommandCatalog() {}

    public static List<CommandSpec> forConfig(BridgeConfig config) {
        List<CommandSpec> commands = new ArrayList<>();
        commands.add(setup());
        commands.add(command(LIST, List.of(), false, false, false));
        if (config.linking().enabled()) {
            commands.add(command(
                    LINK,
                    List.of(option(OptionType.STRING, Linking.CODE_OPTION, "command.link.code", true, false)),
                    false,
                    true,
                    true));
            commands.add(command(UNLINK, List.of(), false, true, true));
        }
        if (config.actions().whitelist()) {
            commands.add(new CommandSpec(
                    WHITELIST,
                    english("command.whitelist"),
                    korean(WHITELIST, "command.whitelist"),
                    List.of(),
                    List.of(whitelistChange(Actions.ADD), whitelistChange(Actions.REMOVE)),
                    true,
                    true,
                    false));
        }
        if (config.actions().kick()) {
            commands.add(command(
                    KICK,
                    List.of(
                            option(OptionType.STRING, Actions.PLAYER_OPTION, "command.player", true, true),
                            option(OptionType.STRING, Actions.REASON_OPTION, "command.kick.reason", false, false)),
                    true,
                    true,
                    false));
        }
        return List.copyOf(commands);
    }

    public static List<CommandSpec> setupOnly() {
        return List.of(setup());
    }

    private static CommandSpec setup() {
        return command(
                SETUP,
                List.of(
                        option(OptionType.STRING, ChannelSetup.ROLE_OPTION, "command.setup.as", false, true),
                        option(OptionType.STRING, ChannelSetup.CODE_OPTION, "command.setup.code", false, false)),
                false,
                true,
                true);
    }

    private static Subcommand whitelistChange(String change) {
        String key = "command.whitelist." + change;
        return new Subcommand(
                change,
                english(key),
                korean(change, key),
                List.of(option(OptionType.STRING, Actions.PLAYER_OPTION, "command.player", true, false)));
    }

    private static CommandSpec command(
            String name, List<Option> options, boolean adminOnly, boolean slow, boolean ephemeral) {
        String key = "command." + name;
        return new CommandSpec(name, english(key), korean(name, key), options, adminOnly, slow, ephemeral);
    }

    private static Option option(OptionType type, String name, String key, boolean required, boolean autocomplete) {
        return new Option(type, name, english(key), korean(name, key), required, autocomplete);
    }

    private static String english(String key) {
        return ENGLISH.text(key);
    }

    private static Localized korean(String name, String key) {
        return new Localized(name, KOREAN.text(key));
    }
}
