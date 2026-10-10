package com.minecraftdiscordbridge.core.commands;

import com.minecraftdiscordbridge.core.actions.Actions;
import com.minecraftdiscordbridge.core.config.BridgeConfig;
import com.minecraftdiscordbridge.core.discord.AutocompleteReply;
import com.minecraftdiscordbridge.core.discord.AutocompleteRequest;
import com.minecraftdiscordbridge.core.discord.CommandReply;
import com.minecraftdiscordbridge.core.discord.CommandRequest;
import com.minecraftdiscordbridge.core.discord.CommandSpec;
import com.minecraftdiscordbridge.core.discord.CommandSpec.Option;
import com.minecraftdiscordbridge.core.discord.CommandSpec.OptionType;
import com.minecraftdiscordbridge.core.discord.CommandSpec.Subcommand;
import com.minecraftdiscordbridge.core.i18n.Lang;
import com.minecraftdiscordbridge.core.i18n.Language;
import com.minecraftdiscordbridge.core.linking.Linking;
import com.minecraftdiscordbridge.core.setup.ChannelSetup;
import com.minecraftdiscordbridge.core.status.Roster;
import com.minecraftdiscordbridge.core.text.markdown.DiscordEscaper;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class DiscordCommands {
    public static final String SETUP = "setup";
    public static final String LIST = "list";
    public static final String LINK = "link";
    public static final String UNLINK = "unlink";
    public static final String WHITELIST = "whitelist";
    public static final String KICK = "kick";

    private static final Lang ENGLISH = Lang.of(Language.EN);
    private static final Lang KOREAN = Lang.of(Language.KO);

    private final ChannelSetup setup;
    private final Linking linking;
    private final Actions actions;
    private final Roster roster;
    private final Lang lang;

    public DiscordCommands(ChannelSetup setup, Linking linking, Actions actions, Roster roster, Lang lang) {
        this.setup = setup;
        this.linking = linking;
        this.actions = actions;
        this.roster = roster;
        this.lang = lang;
    }

    public void onCommand(CommandRequest request, CommandReply reply) {
        switch (request.command()) {
            case SETUP -> setup.handle(request, reply);
            case LIST -> list(request, reply);
            case LINK -> linking.link(request, reply);
            case UNLINK -> linking.unlink(request, reply);
            case WHITELIST -> actions.whitelist(request, reply);
            case KICK -> actions.kick(request, reply);
            default -> reply.replyEphemeral(userLang(request.locale()).text("discord.unknown-command"));
        }
    }

    public void onAutocomplete(AutocompleteRequest request, AutocompleteReply reply) {
        if (request.command().equals(SETUP) && request.option().equals(ChannelSetup.ROLE_OPTION)) {
            setup.autocomplete(request.typed(), userLang(request.locale()), reply);
        } else if (request.command().equals(KICK) && request.option().equals(Actions.PLAYER_OPTION)) {
            reply.respond(onlineNamesStartingWith(request.typed()));
        } else {
            reply.respond(List.of());
        }
    }

    public static List<CommandSpec> forConfig(BridgeConfig config) {
        List<CommandSpec> commands = new ArrayList<>();
        commands.add(setupCommand());
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
                    korean("command.whitelist"),
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
        return List.of(setupCommand());
    }

    private void list(CommandRequest request, CommandReply reply) {
        Lang userLang = userLang(request.locale());
        List<String> names = roster.visibleNames();
        if (names.isEmpty()) {
            reply.reply(userLang.text("discord.list.empty"));
            return;
        }
        String joined =
                String.join(", ", names.stream().map(DiscordEscaper::escape).toList());
        reply.reply(userLang.text("discord.list.players", names.size(), joined));
    }

    private List<AutocompleteReply.Choice> onlineNamesStartingWith(String typed) {
        String prefix = typed.strip().toLowerCase(Locale.ROOT);
        return roster.visibleNames().stream()
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                .limit(AutocompleteReply.MAX_CHOICES)
                .map(name -> new AutocompleteReply.Choice(name, name))
                .toList();
    }

    private Lang userLang(String locale) {
        return Lang.forDiscordLocale(locale, lang);
    }

    private static CommandSpec setupCommand() {
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
                korean(key),
                List.of(option(OptionType.STRING, Actions.PLAYER_OPTION, "command.player", true, false)));
    }

    private static CommandSpec command(
            String name, List<Option> options, boolean adminOnly, boolean slow, boolean ephemeral) {
        String key = "command." + name;
        return new CommandSpec(name, english(key), korean(key), options, adminOnly, slow, ephemeral);
    }

    private static Option option(OptionType type, String name, String key, boolean required, boolean autocomplete) {
        return new Option(type, name, english(key), korean(key), required, autocomplete);
    }

    private static String english(String key) {
        return ENGLISH.text(key);
    }

    private static Optional<String> korean(String key) {
        return Optional.of(KOREAN.text(key));
    }
}
