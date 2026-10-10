package com.minecraftdiscordbridge.core.setup;

import static com.minecraftdiscordbridge.core.log.Log.LOGGER;

import com.minecraftdiscordbridge.core.codes.Attempts;
import com.minecraftdiscordbridge.core.config.DiscordConfig;
import com.minecraftdiscordbridge.core.discord.AutocompleteReply;
import com.minecraftdiscordbridge.core.discord.CommandReply;
import com.minecraftdiscordbridge.core.discord.CommandRequest;
import com.minecraftdiscordbridge.core.discord.Discord;
import com.minecraftdiscordbridge.core.discord.DiscordId;
import com.minecraftdiscordbridge.core.feature.FeatureContext;
import com.minecraftdiscordbridge.core.i18n.Lang;
import com.minecraftdiscordbridge.core.i18n.Replies;
import com.minecraftdiscordbridge.core.log.Failures;
import com.minecraftdiscordbridge.core.loop.IoExecutor;
import com.minecraftdiscordbridge.core.platform.CommandSource;
import com.minecraftdiscordbridge.core.storage.BindingStore;
import com.minecraftdiscordbridge.core.storage.ChannelBinding;
import com.minecraftdiscordbridge.core.storage.ChannelRole;
import com.minecraftdiscordbridge.core.text.Format.Slot;
import java.time.Instant;
import java.time.InstantSource;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public final class ChannelSetup {
    public static final String ROLE_OPTION = "as";
    public static final String CODE_OPTION = "code";

    private final Discord discord;
    private final BindingStore bindings;
    private final DiscordConfig config;
    private final IoExecutor io;
    private final SetupCodes codes;
    private final Attempts attempts;
    private final InstantSource clock;
    private final Replies replies;
    private final Runnable bound;

    public ChannelSetup(
            FeatureContext context,
            DiscordConfig config,
            BindingStore bindings,
            SetupCodes codes,
            Attempts attempts,
            Runnable bound) {
        this.discord = context.discord();
        this.bindings = bindings;
        this.config = config;
        this.io = context.io();
        this.codes = codes;
        this.attempts = attempts;
        this.clock = context.clock();
        this.replies = context.replies();
        this.bound = bound;
    }

    public void handle(CommandRequest request, CommandReply reply) {
        Lang userLang = Lang.forDiscordLocale(request.locale(), replies.lang());
        Optional<ChannelRole> role = role(request.options().get(ROLE_OPTION));
        if (role.isEmpty()) {
            reply.replyEphemeral(userLang.text("discord.setup.bad-role"));
        } else if (config.consoleChannel().equals(Optional.of(request.channelId()))) {
            reply.replyEphemeral(userLang.text("discord.setup.console-channel"));
        } else if (authorized(request, reply, userLang)) {
            save(request, reply, userLang, role.get());
        }
    }

    public void autocomplete(String typed, Lang userLang, AutocompleteReply reply) {
        String prefix = typed.strip().toLowerCase(Locale.ROOT);
        reply.respond(List.of(ChannelRole.CHAT, ChannelRole.EVENTS).stream()
                .map(role -> new AutocompleteReply.Choice(userLang.text("discord.setup.role." + role.id()), role.id()))
                .filter(choice -> choice.value().startsWith(prefix)
                        || choice.name().toLowerCase(Locale.ROOT).startsWith(prefix))
                .toList());
    }

    public void issueCode(CommandSource source) {
        String code = codes.issue(clock.instant());
        replies.send(source, "game.setup.code", Slot.text("code", code));
    }

    private boolean authorized(CommandRequest request, CommandReply reply, Lang userLang) {
        if (discord.isApplicationOwner(request.userId())) {
            return true;
        }
        String typed = request.options().getOrDefault(CODE_OPTION, "");
        if (typed.isBlank()) {
            reply.replyEphemeral(userLang.text("discord.setup.not-owner"));
            return false;
        }
        if (!request.canManageServer()) {
            reply.replyEphemeral(userLang.text("discord.setup.needs-manage-server"));
            return false;
        }
        Instant now = clock.instant();
        if (attempts.blocked(request.userId(), now)) {
            reply.replyEphemeral(userLang.text("discord.too-many-attempts"));
            return false;
        }
        if (!codes.redeem(typed, now)) {
            attempts.failed(request.userId(), now);
            reply.replyEphemeral(userLang.text("discord.setup.bad-code"));
            return false;
        }
        return true;
    }

    private void save(CommandRequest request, CommandReply reply, Lang userLang, ChannelRole role) {
        ChannelBinding next = bind(bindings.current(), request.guildId(), role, request.channelId());
        io.completeOnLoop(
                () -> bindings.save(next),
                () -> {
                    LOGGER.info(
                            "Discord user {} chose channel {} for {}",
                            request.userId(),
                            request.channelId(),
                            role.id());
                    reply.replyEphemeral(confirmation(userLang, role));
                    bound.run();
                },
                failure -> {
                    LOGGER.warn(
                            "Could not save the channel from /setup: {}. Check that the data folder is writable",
                            Failures.describe(failure));
                    reply.replyEphemeral(userLang.text("discord.save-failed"));
                });
    }

    private String confirmation(Lang userLang, ChannelRole role) {
        String done = userLang.text("discord.setup.done." + role.id());
        Optional<DiscordId> configured = role == ChannelRole.CHAT ? config.chatChannel() : config.eventsChannel();
        if (configured.isEmpty()) {
            return done;
        }
        return done + "\n" + userLang.text("discord.setup.config-wins", "discord." + role.id() + "-channel");
    }

    static ChannelBinding bind(Optional<ChannelBinding> current, DiscordId guild, ChannelRole role, DiscordId channel) {
        Map<ChannelRole, DiscordId> channels = new EnumMap<>(ChannelRole.class);
        current.filter(binding -> binding.guildId().equals(guild))
                .ifPresent(binding -> channels.putAll(binding.channels()));
        channels.put(role, channel);
        return new ChannelBinding(guild, channels);
    }

    static Optional<ChannelRole> role(@Nullable String typed) {
        if (typed == null || typed.isBlank()) {
            return Optional.of(ChannelRole.CHAT);
        }
        return ChannelRole.find(typed.strip().toLowerCase(Locale.ROOT));
    }
}
