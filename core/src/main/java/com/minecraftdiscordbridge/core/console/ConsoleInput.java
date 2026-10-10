package com.minecraftdiscordbridge.core.console;

import static com.minecraftdiscordbridge.core.log.Log.LOGGER;

import com.minecraftdiscordbridge.core.actions.GameActions;
import com.minecraftdiscordbridge.core.config.ConsoleConfig;
import com.minecraftdiscordbridge.core.discord.Author;
import com.minecraftdiscordbridge.core.discord.Discord;
import com.minecraftdiscordbridge.core.discord.DiscordId;
import com.minecraftdiscordbridge.core.discord.DiscordMessage;
import com.minecraftdiscordbridge.core.discord.OutboundMessage;
import com.minecraftdiscordbridge.core.feature.FeatureContext;
import com.minecraftdiscordbridge.core.i18n.Lang;
import com.minecraftdiscordbridge.core.platform.ActionOutcome;
import com.minecraftdiscordbridge.core.platform.GameAction;
import com.minecraftdiscordbridge.core.text.SafeText;
import com.minecraftdiscordbridge.core.text.markdown.DiscordEscaper;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

public final class ConsoleInput {
    public static final int MAX_COMMAND_LENGTH = 256;
    static final int MAX_OUTPUT_LENGTH = 1900;
    private static final Author SERVER = Author.server();

    private final Optional<DiscordId> channel;
    private final Set<DiscordId> users;
    private final GameActions game;
    private final Discord discord;
    private final Lang lang;
    private final Supplier<Collection<String>> secrets;

    public ConsoleInput(
            FeatureContext context,
            ConsoleConfig config,
            Optional<DiscordId> channel,
            GameActions game,
            Supplier<Collection<String>> secrets) {
        this.channel = config.inputUsers().isEmpty() ? Optional.empty() : channel;
        this.users = Set.copyOf(config.inputUsers());
        this.game = game;
        this.discord = context.discord();
        this.lang = context.replies().lang();
        this.secrets = secrets;
    }

    public void onMessage(DiscordMessage message) {
        if (channel.isEmpty()
                || !message.channelId().equals(channel.get())
                || !allowed(message.author())
                || message.content().isBlank()) {
            return;
        }
        String command = command(message.content());
        if (command.isEmpty() || command.length() > MAX_COMMAND_LENGTH || command.indexOf('\n') >= 0) {
            post(lang.text("discord.console.invalid", MAX_COMMAND_LENGTH));
            return;
        }
        String user = message.author().id().toString();
        LOGGER.info(
                "Discord user {} ({}) ran the console command: {}",
                user,
                SafeText.strip(message.author().displayName()).replaceAll("\\s+", " "),
                command);
        post(lang.text("discord.console.ran", user, DiscordEscaper.escape(command)));
        game.run(new GameAction.RunConsoleCommand(command), lang, outcome -> post(reply(outcome)));
    }

    static String command(String content) {
        String command = SafeText.strip(content).strip();
        return command.startsWith("/") ? command.substring(1).strip() : command;
    }

    private boolean allowed(DiscordMessage.Sender author) {
        return !author.bot() && !author.webhook() && users.contains(author.id());
    }

    private String reply(ActionOutcome outcome) {
        if (outcome instanceof ActionOutcome.Output output && !output.text().isBlank()) {
            return block(output.text());
        }
        if (outcome instanceof ActionOutcome.Failed failed) {
            return lang.text("discord.console.failed", DiscordEscaper.escape(failed.reason()));
        }
        return lang.text("discord.console.no-output");
    }

    private String block(String output) {
        return ConsoleBlock.fence(ConsoleBlock.body(output.strip(), secrets.get(), MAX_OUTPUT_LENGTH));
    }

    private void post(String text) {
        discord.send(OutboundMessage.of(channel.get(), SERVER, text));
    }
}
