package com.minecraftdiscordbridge.core.actions;

import com.minecraftdiscordbridge.core.i18n.Lang;
import com.minecraftdiscordbridge.core.log.Failures;
import com.minecraftdiscordbridge.core.platform.ActionOutcome;
import com.minecraftdiscordbridge.core.platform.GameAction;
import com.minecraftdiscordbridge.core.platform.Platform;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

public final class GameActions {
    public static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final Platform platform;
    private final Executor loop;
    private final Duration timeout;

    public GameActions(Platform platform, Executor loop, Duration timeout) {
        this.platform = platform;
        this.loop = loop;
        this.timeout = timeout;
    }

    public void run(GameAction action, Lang userLang, Consumer<ActionOutcome> done) {
        CompletableFuture<ActionOutcome> outcome;
        try {
            outcome = platform.perform(action).copy().orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (RuntimeException failure) {
            outcome = CompletableFuture.failedFuture(failure);
        }
        var unused = outcome.whenComplete((result, failure) -> loop.execute(() -> done.accept(
                result != null ? phrased(result, userLang) : new ActionOutcome.Failed(describe(failure, userLang)))));
    }

    private static ActionOutcome phrased(ActionOutcome outcome, Lang userLang) {
        String reason = reasonKey(outcome);
        return reason == null ? outcome : new ActionOutcome.Failed(userLang.text(reason));
    }

    private static @Nullable String reasonKey(ActionOutcome outcome) {
        if (outcome instanceof ActionOutcome.Unsupported) {
            return "discord.action.unsupported";
        }
        if (outcome instanceof ActionOutcome.UnknownCommand) {
            return "discord.action.unknown-command";
        }
        if (outcome instanceof ActionOutcome.ServerStopping) {
            return "discord.action.stopping";
        }
        return null;
    }

    private static String describe(@Nullable Throwable failure, Lang userLang) {
        if (failure == null) {
            return "";
        }
        if (Failures.unwrap(failure) instanceof TimeoutException) {
            return userLang.text("discord.action.timeout");
        }
        return Failures.describe(failure);
    }
}
