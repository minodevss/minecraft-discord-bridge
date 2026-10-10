package com.minecraftdiscordbridge.core.discord.jda;

import com.minecraftdiscordbridge.core.discord.AutocompleteRequest;
import com.minecraftdiscordbridge.core.discord.CommandRequest;
import com.minecraftdiscordbridge.core.discord.CommandSpec;
import com.minecraftdiscordbridge.core.discord.DiscordId;
import com.minecraftdiscordbridge.core.discord.DiscordListener;
import com.minecraftdiscordbridge.core.discord.DiscordMessage;
import com.minecraftdiscordbridge.core.discord.DiscordSettings;
import com.minecraftdiscordbridge.core.discord.MemberLookup;
import com.minecraftdiscordbridge.core.loop.CoreLoop;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberUpdateEvent;
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
                || !settings.channelIds()
                        .contains(new DiscordId(event.getChannel().getIdLong()))) {
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
        JdaCommandReply reply = new JdaCommandReply(event);
        if (spec.slow()) {
            reply.defer(spec.ephemeral());
        }
        Map<String, String> options = new HashMap<>();
        for (OptionMapping option : event.getOptions()) {
            options.put(option.getName(), option.getAsString());
        }
        Member member = event.getMember();
        CommandRequest request = new CommandRequest(
                event.getName(),
                Optional.ofNullable(event.getSubcommandName()),
                options,
                new DiscordId(guild.getIdLong()),
                new DiscordId(event.getChannelIdLong()),
                new DiscordId(event.getUser().getIdLong()),
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
                new DiscordId(guild.getIdLong()),
                new DiscordId(event.getChannelIdLong()),
                new DiscordId(event.getUser().getIdLong()),
                event.getUserLocale().getLocale());
        loop.control(() -> listener.onAutocomplete(request, reply));
    }

    void onMemberUpdate(GuildMemberUpdateEvent event) {
        if (scope.accepts(event.getGuild().getIdLong())) {
            MemberLookup.Member member = JdaGuild.member(event.getMember());
            loop.control(() -> listener.onMemberUpdated(member));
        }
    }

    void onMemberRemove(GuildMemberRemoveEvent event) {
        if (scope.accepts(event.getGuild().getIdLong())) {
            DiscordId userId = new DiscordId(event.getUser().getIdLong());
            loop.control(() -> listener.onMemberLeft(userId));
        }
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
