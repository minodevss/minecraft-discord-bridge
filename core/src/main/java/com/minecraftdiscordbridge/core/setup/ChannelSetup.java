package com.minecraftdiscordbridge.core.setup;

import com.minecraftdiscordbridge.core.codes.Attempts;
import com.minecraftdiscordbridge.core.config.DiscordConfig;
import com.minecraftdiscordbridge.core.discord.AutocompleteReply;
import com.minecraftdiscordbridge.core.discord.Discord;
import com.minecraftdiscordbridge.core.discord.DiscordId;
import com.minecraftdiscordbridge.core.discord.InteractionReply;
import com.minecraftdiscordbridge.core.discord.InteractionRequest;
import com.minecraftdiscordbridge.core.i18n.Lang;
import com.minecraftdiscordbridge.core.i18n.Replies;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ChannelSetup {
    public static final String ROLE_OPTION = "as";
    public static final String CODE_OPTION = "code";
    private static final Logger LOGGER = LoggerFactory.getLogger("MinecraftDiscordBridge");

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
            Discord discord,
            BindingStore bindings,
            DiscordConfig config,
            IoExecutor io,
            SetupCodes codes,
            Attempts attempts,
            InstantSource clock,
            Replies replies,
            Runnable bound) {
        this.discord = discord;
        this.bindings = bindings;
        this.config = config;
        this.io = io;
        this.codes = codes;
        this.attempts = attempts;
        this.clock = clock;
        this.replies = replies;
        this.bound = bound;
    }

    public void handle(InteractionRequest request, InteractionReply reply) {
        Lang reader = Lang.forDiscordLocale(request.locale(), replies.lang());
        Optional<ChannelRole> role = role(request.options().get(ROLE_OPTION));
        if (role.isEmpty()) {
            reply.replyEphemeral(reader.text("discord.setup.bad-role"));
        } else if (config.consoleChannel().equals(Optional.of(new DiscordId(request.channelId())))) {
            reply.replyEphemeral(reader.text("discord.setup.console-channel"));
        } else if (authorized(request, reply, reader)) {
            save(request, reply, reader, role.get());
        }
    }

    public void autocomplete(String typed, Lang reader, AutocompleteReply reply) {
        String prefix = typed.strip().toLowerCase(Locale.ROOT);
        reply.respond(List.of(ChannelRole.CHAT, ChannelRole.EVENTS).stream()
                .map(role -> new AutocompleteReply.Choice(reader.text("discord.setup.role." + id(role)), id(role)))
                .filter(choice -> choice.value().startsWith(prefix)
                        || choice.name().toLowerCase(Locale.ROOT).startsWith(prefix))
                .toList());
    }

    public void issueCode(CommandSource source) {
        String code = codes.issue(clock.instant());
        replies.send(source, "game.setup.code", Slot.text("code", code));
    }

    private boolean authorized(InteractionRequest request, InteractionReply reply, Lang reader) {
        if (discord.isApplicationOwner(request.userId())) {
            return true;
        }
        String typed = request.options().getOrDefault(CODE_OPTION, "");
        if (typed.isBlank()) {
            reply.replyEphemeral(reader.text("discord.setup.not-owner"));
            return false;
        }
        if (!request.canManageServer()) {
            reply.replyEphemeral(reader.text("discord.setup.needs-manage-server"));
            return false;
        }
        Instant now = clock.instant();
        if (attempts.blocked(request.userId(), now)) {
            reply.replyEphemeral(reader.text("discord.too-many-attempts"));
            return false;
        }
        if (!codes.redeem(typed, now)) {
            attempts.failed(request.userId(), now);
            reply.replyEphemeral(reader.text("discord.setup.bad-code"));
            return false;
        }
        return true;
    }

    private void save(InteractionRequest request, InteractionReply reply, Lang reader, ChannelRole role) {
        ChannelBinding next = bind(bindings.current(), request.guildId(), role, request.channelId());
        io.run(
                () -> bindings.save(next),
                () -> {
                    LOGGER.info(
                            "Discord user {} chose channel {} for {}",
                            Long.toUnsignedString(request.userId()),
                            Long.toUnsignedString(request.channelId()),
                            id(role));
                    reply.replyEphemeral(confirmation(reader, role));
                    bound.run();
                },
                failure -> {
                    LOGGER.warn(
                            "Could not save the channel from /setup: {}. Check that the data folder is writable",
                            failure.getMessage());
                    reply.replyEphemeral(reader.text("discord.save-failed"));
                });
    }

    private String confirmation(Lang reader, ChannelRole role) {
        String done = reader.text("discord.setup.done." + id(role));
        Optional<DiscordId> configured = role == ChannelRole.CHAT ? config.chatChannel() : config.eventsChannel();
        if (configured.isEmpty()) {
            return done;
        }
        return done + "\n" + reader.text("discord.setup.config-wins", "discord." + id(role) + "-channel");
    }

    static ChannelBinding bind(Optional<ChannelBinding> current, long guildId, ChannelRole role, long channelId) {
        DiscordId guild = new DiscordId(guildId);
        Map<ChannelRole, DiscordId> channels = new EnumMap<>(ChannelRole.class);
        current.filter(binding -> binding.guildId().equals(guild))
                .ifPresent(binding -> channels.putAll(binding.channels()));
        channels.put(role, new DiscordId(channelId));
        return new ChannelBinding(guild, channels);
    }

    static Optional<ChannelRole> role(@Nullable String typed) {
        if (typed == null || typed.isBlank()) {
            return Optional.of(ChannelRole.CHAT);
        }
        String id = typed.strip().toLowerCase(Locale.ROOT);
        if (id.equals("chat")) {
            return Optional.of(ChannelRole.CHAT);
        }
        if (id.equals("events")) {
            return Optional.of(ChannelRole.EVENTS);
        }
        return Optional.empty();
    }

    private static String id(ChannelRole role) {
        return role.name().toLowerCase(Locale.ROOT);
    }
}
