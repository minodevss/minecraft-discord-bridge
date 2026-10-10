package com.minecraftdiscordbridge.core.discord;

import com.minecraftdiscordbridge.core.loop.CoreLoop;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.jspecify.annotations.Nullable;

final class JdaEvents {
    static final String NOT_SET_UP = "This bot is not set up for this server.";
    static final String UNKNOWN_COMMAND = "This command is no longer available.";

    private final DiscordSettings settings;
    private final GuildScope scope;
    private final CoreLoop loop;
    private final DiscordListener listener;
    private final WebhookSender webhooks;
    private final Map<String, CommandSpec> commands;

    JdaEvents(
            DiscordSettings settings,
            GuildScope scope,
            CoreLoop loop,
            DiscordListener listener,
            WebhookSender webhooks) {
        this.settings = settings;
        this.scope = scope;
        this.loop = loop;
        this.listener = listener;
        this.webhooks = webhooks;
        this.commands = settings.commands().stream()
                .collect(Collectors.toUnmodifiableMap(CommandSpec::name, Function.identity()));
    }

    void onMessage(MessageReceivedEvent event) {
        if (!event.isFromGuild()
                || !scope.accepts(event.getGuild().getIdLong())
                || !settings.channelIds().contains(event.getChannel().getIdLong())) {
            return;
        }
        Message message = event.getMessage();
        if (message.getType().isSystem() || !origin(message).relayed()) {
            return;
        }
        DiscordMessage mapped = MessageMapper.map(message);
        loop.content(() -> listener.onMessage(mapped));
    }

    void onCommand(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        if (guild == null || !scope.accepts(guild.getIdLong())) {
            refuse(event, NOT_SET_UP);
            return;
        }
        CommandSpec spec = commands.get(event.getName());
        if (spec == null) {
            refuse(event, UNKNOWN_COMMAND);
            return;
        }
        JdaInteractionReply reply = new JdaInteractionReply(event);
        if (spec.slow()) {
            reply.defer(spec.ephemeral());
        }
        Map<String, String> options = new HashMap<>();
        for (OptionMapping option : event.getOptions()) {
            options.put(option.getName(), option.getAsString());
        }
        Member member = event.getMember();
        InteractionRequest request = new InteractionRequest(
                event.getName(),
                event.getSubcommandName(),
                options,
                guild.getIdLong(),
                event.getChannelIdLong(),
                event.getUser().getIdLong(),
                displayName(member, event.getUser().getEffectiveName()),
                member != null && member.hasPermission(Permission.MANAGE_SERVER),
                event.getUserLocale().getLocale());
        loop.control(() -> listener.onCommand(request, reply));
    }

    void onAutocomplete(CommandAutoCompleteInteractionEvent event) {
        JdaAutocompleteReply reply = new JdaAutocompleteReply(event);
        Guild guild = event.getGuild();
        if (guild == null || !scope.accepts(guild.getIdLong()) || !commands.containsKey(event.getName())) {
            reply.respond(List.of());
            return;
        }
        AutocompleteRequest request = new AutocompleteRequest(
                event.getName(),
                event.getFocusedOption().getName(),
                event.getFocusedOption().getValue(),
                guild.getIdLong(),
                event.getChannelIdLong(),
                event.getUser().getIdLong(),
                event.getUserLocale().getLocale());
        loop.control(() -> listener.onAutocomplete(request, reply));
    }

    private Origin origin(Message message) {
        JDA jda = message.getJDA();
        long authorId = message.getAuthor().getIdLong();
        if (authorId == jda.getSelfUser().getIdLong()) {
            return Origin.SELF;
        }
        if (message.isWebhookMessage()) {
            boolean ours = webhooks.isOwnWebhook(authorId)
                    || message.getApplicationIdLong() == jda.getSelfUser().getApplicationIdLong();
            return ours ? Origin.OWN_WEBHOOK : Origin.WEBHOOK;
        }
        return message.getAuthor().isBot() ? Origin.BOT : Origin.USER;
    }

    private static void refuse(IReplyCallback interaction, String text) {
        interaction.reply(Messages.create(text)).setEphemeral(true).queue(null, failure -> {});
    }

    private static String displayName(@Nullable Member member, String fallback) {
        return member == null ? fallback : member.getEffectiveName();
    }
}
