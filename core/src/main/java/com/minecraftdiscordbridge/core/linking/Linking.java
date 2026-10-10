package com.minecraftdiscordbridge.core.linking;

import static com.minecraftdiscordbridge.core.log.Log.LOGGER;

import com.minecraftdiscordbridge.core.codes.Attempts;
import com.minecraftdiscordbridge.core.config.LinkingConfig;
import com.minecraftdiscordbridge.core.discord.CommandReply;
import com.minecraftdiscordbridge.core.discord.CommandRequest;
import com.minecraftdiscordbridge.core.discord.DiscordId;
import com.minecraftdiscordbridge.core.i18n.Lang;
import com.minecraftdiscordbridge.core.i18n.Replies;
import com.minecraftdiscordbridge.core.log.Failures;
import com.minecraftdiscordbridge.core.loop.IoExecutor;
import com.minecraftdiscordbridge.core.platform.CommandSource;
import com.minecraftdiscordbridge.core.platform.PlayerProfile;
import com.minecraftdiscordbridge.core.status.Roster;
import com.minecraftdiscordbridge.core.storage.Edition;
import com.minecraftdiscordbridge.core.storage.Link;
import com.minecraftdiscordbridge.core.storage.LinkStore;
import com.minecraftdiscordbridge.core.text.Format.Slot;
import com.minecraftdiscordbridge.core.text.markdown.DiscordEscaper;
import java.time.Instant;
import java.time.InstantSource;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;

public final class Linking {
    public static final String CODE_OPTION = "code";

    private final LinkingConfig config;
    private final LinkStore store;
    private final IoExecutor io;
    private final PendingLinks pending;
    private final Attempts attempts;
    private final Roster roster;
    private final InstantSource clock;
    private final Replies replies;
    private final BooleanSupplier discordConnected;

    public Linking(
            LinkingConfig config,
            LinkStore store,
            IoExecutor io,
            PendingLinks pending,
            Attempts attempts,
            Roster roster,
            InstantSource clock,
            Replies replies,
            BooleanSupplier discordConnected) {
        this.config = config;
        this.store = store;
        this.io = io;
        this.pending = pending;
        this.attempts = attempts;
        this.roster = roster;
        this.clock = clock;
        this.replies = replies;
        this.discordConnected = discordConnected;
    }

    public void linkInGame(CommandSource source) {
        UUID player = source.playerId();
        if (!config.enabled()) {
            replies.send(source, "game.link.disabled");
        } else if (player == null) {
            replies.send(source, "game.players-only");
        } else if (store.byMinecraftId(player).isPresent()) {
            replies.send(source, "game.link.already");
        } else if (!discordConnected.getAsBoolean()) {
            replies.send(source, "game.link.offline");
        } else {
            String code = pending.issue(player, source.name(), isBedrock(player), source, clock.instant());
            replies.send(source, "game.link.code", Slot.component("code", copyable(code)));
        }
    }

    public void unlinkInGame(CommandSource source) {
        UUID player = source.playerId();
        if (player == null) {
            replies.send(source, "game.players-only");
            return;
        }
        io.completeOnLoop(
                () -> store.unlinkMinecraft(player),
                removed -> replies.send(source, removed.isPresent() ? "game.unlink.done" : "game.unlink.none"),
                failure -> {
                    LOGGER.warn(
                            "Could not unlink {}: {}. Check that the data folder is writable",
                            source.name(),
                            Failures.describe(failure));
                    replies.send(source, "game.save-failed");
                });
    }

    public void link(CommandRequest request, CommandReply reply) {
        Lang userLang = Lang.forDiscordLocale(request.locale(), replies.lang());
        Instant now = clock.instant();
        if (!config.enabled()) {
            reply.replyEphemeral(userLang.text("discord.link.disabled"));
            return;
        }
        if (attempts.blocked(request.userId(), now)) {
            reply.replyEphemeral(userLang.text("discord.too-many-attempts"));
            return;
        }
        Optional<PendingLinks.Pending> found = pending.redeem(request.options().getOrDefault(CODE_OPTION, ""), now);
        if (found.isEmpty()) {
            attempts.failed(request.userId(), now);
            reply.replyEphemeral(userLang.text("discord.link.bad-code"));
            return;
        }
        PendingLinks.Pending code = found.get();
        DiscordId discordId = request.userId();
        Link link = new Link(
                code.playerId(), discordId, code.playerName(), code.bedrock() ? Edition.BEDROCK : Edition.JAVA, now);
        io.completeOnLoop(
                () -> store.link(link),
                () -> {
                    LOGGER.info("Linked Minecraft account {} to Discord user {}", code.playerName(), discordId);
                    reply.replyEphemeral(userLang.text("discord.link.done", DiscordEscaper.escape(code.playerName())));
                    if (roster.find(code.playerId()).isPresent()) {
                        replies.send(code.source(), "game.link.done", Slot.text("name", request.userDisplayName()));
                    }
                },
                failure -> {
                    LOGGER.warn(
                            "Could not save the link for {}: {}. Check that the data folder is writable",
                            code.playerName(),
                            Failures.describe(failure));
                    reply.replyEphemeral(userLang.text("discord.save-failed"));
                });
    }

    public void unlink(CommandRequest request, CommandReply reply) {
        Lang userLang = Lang.forDiscordLocale(request.locale(), replies.lang());
        io.completeOnLoop(
                () -> store.unlinkDiscord(request.userId()),
                removed -> reply.replyEphemeral(removed.map(link ->
                                userLang.text("discord.unlink.done", DiscordEscaper.escape(link.minecraftName())))
                        .orElseGet(() -> userLang.text("discord.unlink.none"))),
                failure -> {
                    LOGGER.warn(
                            "Could not unlink Discord user {}: {}. Check that the data folder is writable",
                            request.userId(),
                            Failures.describe(failure));
                    reply.replyEphemeral(userLang.text("discord.save-failed"));
                });
    }

    private boolean isBedrock(UUID player) {
        return roster.find(player).map(PlayerProfile::bedrock).orElse(player.getMostSignificantBits() == 0);
    }

    private Component copyable(String code) {
        return Component.text(code, NamedTextColor.AQUA)
                .clickEvent(ClickEvent.copyToClipboard(code))
                .hoverEvent(HoverEvent.showText(replies.lang().component("game.link.copy")));
    }
}
