package com.minecraftdiscordbridge.core.actions;

import static com.minecraftdiscordbridge.core.log.Log.LOGGER;

import com.minecraftdiscordbridge.core.config.ActionsConfig;
import com.minecraftdiscordbridge.core.discord.CommandReply;
import com.minecraftdiscordbridge.core.discord.CommandRequest;
import com.minecraftdiscordbridge.core.i18n.Lang;
import com.minecraftdiscordbridge.core.platform.ActionOutcome;
import com.minecraftdiscordbridge.core.platform.GameAction;
import com.minecraftdiscordbridge.core.platform.Platform;
import com.minecraftdiscordbridge.core.text.Graphemes;
import com.minecraftdiscordbridge.core.text.SafeText;
import com.minecraftdiscordbridge.core.text.markdown.DiscordEscaper;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

public final class Actions {
    public static final String PLAYER_OPTION = "player";
    public static final String REASON_OPTION = "reason";
    public static final String ADD = "add";
    public static final String REMOVE = "remove";
    public static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final Pattern PLAYER_NAME = Pattern.compile("[A-Za-z0-9_.]{1,16}");
    private static final int MAX_REASON_LENGTH = 256;

    private final ActionsConfig config;
    private final Platform platform;
    private final Executor loop;
    private final Lang lang;
    private final Duration timeout;

    public Actions(ActionsConfig config, Platform platform, Executor loop, Lang lang, Duration timeout) {
        this.config = config;
        this.platform = platform;
        this.loop = loop;
        this.lang = lang;
        this.timeout = timeout;
    }

    public static boolean isPlayerName(String name) {
        return PLAYER_NAME.matcher(name).matches();
    }

    public void whitelist(CommandRequest request, CommandReply reply) {
        Lang userLang = Lang.forDiscordLocale(request.locale(), lang);
        String change = request.subcommand().orElse("");
        if (!config.whitelist()) {
            reply.replyEphemeral(userLang.text("discord.action.disabled"));
        } else if (ADD.equals(change)) {
            perform(request, reply, userLang, new GameAction.WhitelistAdd(player(request)));
        } else if (REMOVE.equals(change)) {
            perform(request, reply, userLang, new GameAction.WhitelistRemove(player(request)));
        } else {
            reply.replyEphemeral(userLang.text("discord.unknown-command"));
        }
    }

    public void kick(CommandRequest request, CommandReply reply) {
        Lang userLang = Lang.forDiscordLocale(request.locale(), lang);
        if (!config.kick()) {
            reply.replyEphemeral(userLang.text("discord.action.disabled"));
            return;
        }
        String reason = SafeText.strip(request.options().getOrDefault(REASON_OPTION, ""))
                .strip();
        if (reason.isEmpty()) {
            reason = lang.text("plain.kick-reason", SafeText.strip(request.userDisplayName()));
        }
        perform(
                request,
                reply,
                userLang,
                new GameAction.Kick(player(request), Graphemes.fit(reason, MAX_REASON_LENGTH)));
    }

    private void perform(CommandRequest request, CommandReply reply, Lang userLang, GameAction action) {
        String name = name(action);
        if (!isPlayerName(name)) {
            reply.replyEphemeral(userLang.text("discord.action.invalid-name", DiscordEscaper.escape(name)));
            return;
        }
        CompletableFuture<ActionOutcome> outcome;
        try {
            outcome = platform.perform(action).copy().orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (RuntimeException failure) {
            outcome = CompletableFuture.failedFuture(failure);
        }
        var unused = outcome.whenComplete((result, failure) -> loop.execute(() -> {
            ActionOutcome done = result != null ? result : new ActionOutcome.Failed(describe(failure, userLang));
            audit(request, action, done);
            reply.reply(userLang.text(outcomeKey(action, done), DiscordEscaper.escape(name), detail(done)));
        }));
    }

    private static void audit(CommandRequest request, GameAction action, ActionOutcome outcome) {
        LOGGER.info(
                "Discord user {} ({}) ran {} {}: {}",
                request.userId(),
                SafeText.strip(request.userDisplayName()).replaceAll("\\s+", " "),
                verb(action),
                name(action),
                outcome.getClass().getSimpleName());
    }

    private static String outcomeKey(GameAction action, ActionOutcome outcome) {
        String verb = verb(action);
        if (outcome instanceof ActionOutcome.Done) {
            return "discord.action." + verb + ".done";
        }
        if (outcome instanceof ActionOutcome.Unchanged) {
            return "discord.action." + verb + ".unchanged";
        }
        if (outcome instanceof ActionOutcome.NoSuchPlayer) {
            return action instanceof GameAction.Kick ? "discord.action.not-online" : "discord.action.no-such-player";
        }
        return "discord.action.failed";
    }

    private static String detail(ActionOutcome outcome) {
        return outcome instanceof ActionOutcome.Failed failed ? DiscordEscaper.escape(failed.reason()) : "";
    }

    private static String describe(@Nullable Throwable failure, Lang userLang) {
        Throwable cause =
                failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
        if (cause instanceof TimeoutException) {
            return userLang.text("discord.action.timeout");
        }
        return cause == null ? "" : String.valueOf(cause.getMessage());
    }

    private static String verb(GameAction action) {
        if (action instanceof GameAction.WhitelistAdd) {
            return "whitelist-add";
        }
        if (action instanceof GameAction.WhitelistRemove) {
            return "whitelist-remove";
        }
        return "kick";
    }

    private static String name(GameAction action) {
        if (action instanceof GameAction.WhitelistAdd add) {
            return add.playerName();
        }
        if (action instanceof GameAction.WhitelistRemove remove) {
            return remove.playerName();
        }
        return ((GameAction.Kick) action).playerName();
    }

    private static String player(CommandRequest request) {
        return request.options().getOrDefault(PLAYER_OPTION, "").strip();
    }
}
