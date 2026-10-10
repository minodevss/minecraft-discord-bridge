package com.minecraftdiscordbridge.core.commands;

import com.minecraftdiscordbridge.core.actions.Actions;
import com.minecraftdiscordbridge.core.discord.AutocompleteReply;
import com.minecraftdiscordbridge.core.discord.AutocompleteRequest;
import com.minecraftdiscordbridge.core.discord.InteractionReply;
import com.minecraftdiscordbridge.core.discord.InteractionRequest;
import com.minecraftdiscordbridge.core.i18n.Lang;
import com.minecraftdiscordbridge.core.linking.Linking;
import com.minecraftdiscordbridge.core.setup.ChannelSetup;
import com.minecraftdiscordbridge.core.status.Roster;
import com.minecraftdiscordbridge.core.text.DiscordEscaper;
import java.util.List;
import java.util.Locale;

public final class SlashRouter {
    private final ChannelSetup setup;
    private final Linking linking;
    private final Actions actions;
    private final Roster roster;
    private final Lang lang;

    public SlashRouter(ChannelSetup setup, Linking linking, Actions actions, Roster roster, Lang lang) {
        this.setup = setup;
        this.linking = linking;
        this.actions = actions;
        this.roster = roster;
        this.lang = lang;
    }

    public void onCommand(InteractionRequest request, InteractionReply reply) {
        switch (request.command()) {
            case SlashCommandCatalog.SETUP -> setup.handle(request, reply);
            case SlashCommandCatalog.LIST -> list(request, reply);
            case SlashCommandCatalog.LINK -> linking.link(request, reply);
            case SlashCommandCatalog.UNLINK -> linking.unlink(request, reply);
            case SlashCommandCatalog.WHITELIST -> actions.whitelist(request, reply);
            case SlashCommandCatalog.KICK -> actions.kick(request, reply);
            default -> reply.replyEphemeral(reader(request.locale()).text("discord.unknown-command"));
        }
    }

    public void onAutocomplete(AutocompleteRequest request, AutocompleteReply reply) {
        if (request.command().equals(SlashCommandCatalog.SETUP)
                && request.option().equals(ChannelSetup.ROLE_OPTION)) {
            setup.autocomplete(request.typed(), reader(request.locale()), reply);
        } else if (request.command().equals(SlashCommandCatalog.KICK)
                && request.option().equals(Actions.PLAYER_OPTION)) {
            reply.respond(onlineNamesStartingWith(request.typed()));
        } else {
            reply.respond(List.of());
        }
    }

    private void list(InteractionRequest request, InteractionReply reply) {
        Lang reader = reader(request.locale());
        List<String> names = roster.visibleNames();
        if (names.isEmpty()) {
            reply.reply(reader.text("discord.list.empty"));
            return;
        }
        String joined =
                String.join(", ", names.stream().map(DiscordEscaper::escape).toList());
        reply.reply(reader.text("discord.list.players", names.size(), joined));
    }

    private List<AutocompleteReply.Choice> onlineNamesStartingWith(String typed) {
        String prefix = typed.strip().toLowerCase(Locale.ROOT);
        return roster.visibleNames().stream()
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                .limit(AutocompleteReply.MAX_CHOICES)
                .map(name -> new AutocompleteReply.Choice(name, name))
                .toList();
    }

    private Lang reader(String locale) {
        return Lang.forDiscordLocale(locale, lang);
    }
}
