package com.minecraftdiscordbridge.core.console;

import com.minecraftdiscordbridge.core.config.ConsoleConfig;
import com.minecraftdiscordbridge.core.discord.Author;
import com.minecraftdiscordbridge.core.discord.Discord;
import com.minecraftdiscordbridge.core.discord.DiscordId;
import com.minecraftdiscordbridge.core.discord.DiscordMessage;
import com.minecraftdiscordbridge.core.discord.OutboundMessage;
import com.minecraftdiscordbridge.core.i18n.Lang;
import com.minecraftdiscordbridge.core.platform.ActionOutcome;
import com.minecraftdiscordbridge.core.platform.GameAction;
import com.minecraftdiscordbridge.core.platform.Platform;
import com.minecraftdiscordbridge.core.text.DiscordEscaper;
import com.minecraftdiscordbridge.core.text.SafeText;
import java.time.Duration;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ConsoleInput {
    public static final int MAX_COMMAND_LENGTH = 256;
    static final int MAX_OUTPUT_LENGTH = 1900;
    private static final String FENCE = "```";
    private static final Author SERVER = Author.server();
    private static final Logger LOGGER = LoggerFactory.getLogger("MinecraftDiscordBridge");

    private final Optional<DiscordId> channel;
    private final Set<Long> users;
    private final Platform platform;
    private final Discord discord;
    private final Executor loop;
    private final Lang lang;
    private final Duration timeout;
    private final Supplier<Collection<String>> secrets;

    public ConsoleInput(
            ConsoleConfig config,
            Optional<DiscordId> channel,
            Platform platform,
            Discord discord,
            Executor loop,
            Lang lang,
            Duration timeout,
            Supplier<Collection<String>> secrets) {
        this.channel = config.inputUsers().isEmpty() ? Optional.empty() : channel;
        this.users = config.inputUsers().stream().map(DiscordId::value).collect(Collectors.toUnmodifiableSet());
        this.platform = platform;
        this.discord = discord;
        this.loop = loop;
        this.lang = lang;
        this.timeout = timeout;
        this.secrets = secrets;
    }

    public void onMessage(DiscordMessage message) {
        if (channel.isEmpty()
                || message.channelId() != channel.get().value()
                || !allowed(message.author())
                || message.content().isBlank()) {
            return;
        }
        String command = command(message.content());
        if (command.isEmpty() || command.length() > MAX_COMMAND_LENGTH || command.indexOf('\n') >= 0) {
            post(lang.text("discord.console.invalid", MAX_COMMAND_LENGTH));
            return;
        }
        String user = Long.toUnsignedString(message.author().id());
        LOGGER.info(
                "Discord user {} ({}) ran the console command: {}",
                user,
                SafeText.strip(message.author().displayName()).replaceAll("\\s+", " "),
                command);
        post(lang.text("discord.console.ran", user, DiscordEscaper.escape(command)));
        run(command);
    }

    static String command(String content) {
        String command = SafeText.strip(content).strip();
        return command.startsWith("/") ? command.substring(1).strip() : command;
    }

    private boolean allowed(DiscordMessage.Sender author) {
        return !author.bot() && !author.webhook() && users.contains(author.id());
    }

    private void run(String command) {
        CompletableFuture<ActionOutcome> outcome;
        try {
            outcome = platform.perform(new GameAction.RunConsoleCommand(command))
                    .copy()
                    .orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (RuntimeException failure) {
            outcome = CompletableFuture.failedFuture(failure);
        }
        var unused = outcome.whenComplete((result, failure) -> loop.execute(() -> post(reply(result, failure))));
    }

    private String reply(@Nullable ActionOutcome outcome, @Nullable Throwable failure) {
        if (outcome instanceof ActionOutcome.Output output && !output.text().isBlank()) {
            return block(output.text());
        }
        if (outcome instanceof ActionOutcome.Failed failed) {
            return lang.text("discord.console.failed", DiscordEscaper.escape(failed.reason()));
        }
        if (outcome == null) {
            return lang.text("discord.console.failed", describe(failure));
        }
        return lang.text("discord.console.no-output");
    }

    private String block(String output) {
        String body = DiscordEscaper.escapeCodeBlock(ConsoleRedactor.redact(output.strip(), secrets.get()));
        if (body.length() > MAX_OUTPUT_LENGTH) {
            body = body.substring(0, MAX_OUTPUT_LENGTH - 1) + "…";
        }
        return FENCE + "\n" + body + "\n" + FENCE;
    }

    private String describe(@Nullable Throwable failure) {
        Throwable cause =
                failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
        if (cause instanceof TimeoutException) {
            return lang.text("discord.action.timeout");
        }
        return cause == null ? "" : DiscordEscaper.escape(String.valueOf(cause.getMessage()));
    }

    private void post(String text) {
        discord.send(OutboundMessage.of(channel.get().value(), SERVER, text));
    }
}
