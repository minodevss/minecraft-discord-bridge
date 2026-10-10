package com.minecraftdiscordbridge.core.actions;

import com.minecraftdiscordbridge.core.i18n.Lang;
import com.minecraftdiscordbridge.core.platform.ActionOutcome;
import com.minecraftdiscordbridge.core.platform.GameAction;
import com.minecraftdiscordbridge.core.platform.Platform;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
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
        var unused = outcome.whenComplete((result, failure) -> loop.execute(
                () -> done.accept(result != null ? result : new ActionOutcome.Failed(describe(failure, userLang)))));
    }

    private static String describe(@Nullable Throwable failure, Lang userLang) {
        Throwable cause =
                failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
        if (cause instanceof TimeoutException) {
            return userLang.text("discord.action.timeout");
        }
        return cause == null ? "" : String.valueOf(cause.getMessage());
    }
}
