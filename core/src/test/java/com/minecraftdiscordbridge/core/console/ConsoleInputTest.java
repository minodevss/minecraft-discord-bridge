package com.minecraftdiscordbridge.core.console;

import static org.assertj.core.api.Assertions.assertThat;

import com.minecraftdiscordbridge.core.FakePlatform;
import com.minecraftdiscordbridge.core.config.ConsoleConfig;
import com.minecraftdiscordbridge.core.discord.Discord;
import com.minecraftdiscordbridge.core.discord.DiscordId;
import com.minecraftdiscordbridge.core.discord.DiscordMessage;
import com.minecraftdiscordbridge.core.discord.DiscordStatus;
import com.minecraftdiscordbridge.core.discord.OutboundMessage;
import com.minecraftdiscordbridge.core.i18n.Lang;
import com.minecraftdiscordbridge.core.i18n.Language;
import com.minecraftdiscordbridge.core.platform.ActionOutcome;
import com.minecraftdiscordbridge.core.platform.GameAction;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConsoleInputTest {
    private static final long CONSOLE = 444444444444444444L;
    private static final long CHAT = 222222222222222222L;
    private static final long ADMIN = 555555555555555555L;
    private static final long MEMBER = 666666666666666666L;
    private static final ConsoleConfig ADMIN_ONLY = new ConsoleConfig(false, List.of(new DiscordId(ADMIN)));

    @TempDir
    Path directory;

    private final SentMessages discord = new SentMessages();

    @Test
    void runsCommandsFromListedUsersAndRepliesWithTheOutput() {
        FakePlatform platform = platform(new ActionOutcome.Output("There are 0 of a max of 20 players online"));

        input(ADMIN_ONLY, platform).onMessage(message(CONSOLE, ADMIN, false, false, "/list"));

        assertThat(platform.actions).containsExactly(new GameAction.RunConsoleCommand("list"));
        assertThat(discord.texts())
                .containsExactly(
                        "<@555555555555555555> ran a console command: list",
                        "```\nThere are 0 of a max of 20 players online\n```");
        assertThat(discord.sent).allSatisfy(sent -> {
            assertThat(sent.channelId()).isEqualTo(CONSOLE);
            assertThat(sent.pingUserIds()).isEmpty();
        });
    }

    @Test
    void ignoresUnlistedUsersBotsAndWebhooks() {
        FakePlatform platform = platform(new ActionOutcome.Done());
        ConsoleInput input = input(ADMIN_ONLY, platform);

        input.onMessage(message(CONSOLE, MEMBER, false, false, "op Steve"));
        input.onMessage(message(CONSOLE, ADMIN, true, false, "op Steve"));
        input.onMessage(message(CONSOLE, ADMIN, false, true, "op Steve"));

        assertThat(platform.actions).isEmpty();
        assertThat(discord.sent).isEmpty();
    }

    @Test
    void ignoresOtherChannelsAndStaysOffWithoutListedUsers() {
        FakePlatform platform = platform(new ActionOutcome.Done());

        input(ADMIN_ONLY, platform).onMessage(message(CHAT, ADMIN, false, false, "stop"));
        input(new ConsoleConfig(true, List.of()), platform).onMessage(message(CONSOLE, ADMIN, false, false, "stop"));

        assertThat(platform.actions).isEmpty();
        assertThat(discord.sent).isEmpty();
    }

    @Test
    void rejectsEmptyMultiLineAndOverlongCommands() {
        FakePlatform platform = platform(new ActionOutcome.Done());
        ConsoleInput input = input(ADMIN_ONLY, platform);

        input.onMessage(message(CONSOLE, ADMIN, false, false, "/"));
        input.onMessage(message(CONSOLE, ADMIN, false, false, "say a\nstop"));
        input.onMessage(message(CONSOLE, ADMIN, false, false, "say " + "x".repeat(253)));

        assertThat(platform.actions).isEmpty();
        assertThat(discord.texts())
                .hasSize(3)
                .allMatch(text ->
                        text.equals("Send one console command per message, on one line, with up to 256 characters."));
    }

    @Test
    void acceptsCommandsOfExactlyTheLengthLimit() {
        FakePlatform platform = platform(new ActionOutcome.Done());
        String command = "say " + "x".repeat(252);

        input(ADMIN_ONLY, platform).onMessage(message(CONSOLE, ADMIN, false, false, command));

        assertThat(platform.actions).containsExactly(new GameAction.RunConsoleCommand(command));
        assertThat(discord.texts()).last().isEqualTo("The command ran with no output.");
    }

    @Test
    void escapesAndShortensTheOutputBlock() {
        FakePlatform platform = platform(new ActionOutcome.Output("```\nleaked``` " + "y".repeat(3000)));

        input(ADMIN_ONLY, platform).onMessage(message(CONSOLE, ADMIN, false, false, "help"));

        String block = discord.texts().get(1);
        assertThat(block).startsWith("```\n").endsWith("…\n```").hasSizeLessThan(2000);
        assertThat(block.substring(3, block.length() - 3)).doesNotContain("```");
    }

    @Test
    void escapesMarkdownInTheAuditLine() {
        FakePlatform platform = platform(new ActionOutcome.Done());

        input(ADMIN_ONLY, platform).onMessage(message(CONSOLE, ADMIN, false, false, "say **hi** @everyone"));

        assertThat(discord.texts().get(0)).endsWith("say \\*\\*hi\\*\\* @everyone");
    }

    @Test
    void reportsCommandsThatTimeOut() {
        FakePlatform platform = new FakePlatform(directory);
        platform.outcomes = action -> new CompletableFuture<>();

        input(ADMIN_ONLY, platform).onMessage(message(CONSOLE, ADMIN, false, false, "save-all"));

        assertThat(discord.awaitTexts(2).get(1))
                .isEqualTo("The command did not run: the server did not answer in time");
    }

    private ConsoleInput input(ConsoleConfig config, FakePlatform platform) {
        return new ConsoleInput(
                config,
                Optional.of(new DiscordId(CONSOLE)),
                platform,
                discord,
                Runnable::run,
                Lang.of(Language.EN),
                Duration.ofMillis(100),
                List::of);
    }

    private FakePlatform platform(ActionOutcome outcome) {
        FakePlatform platform = new FakePlatform(directory);
        platform.outcomes = action -> CompletableFuture.completedFuture(outcome);
        return platform;
    }

    private static DiscordMessage message(long channel, long author, boolean bot, boolean webhook, String content) {
        return new DiscordMessage(
                channel,
                new DiscordMessage.Sender(author, "Admin", null, bot, webhook),
                content,
                Map.of(),
                Map.of(),
                Map.of(),
                List.of(),
                null,
                List.of(),
                false,
                false,
                false);
    }

    private static final class SentMessages implements Discord {
        final List<OutboundMessage> sent = new CopyOnWriteArrayList<>();

        List<String> texts() {
            return sent.stream().map(OutboundMessage::text).toList();
        }

        List<String> awaitTexts(int count) {
            long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
            while (sent.size() < count && System.nanoTime() < deadline) {
                Thread.onSpinWait();
            }
            return texts();
        }

        @Override
        public void start() {}

        @Override
        public DiscordStatus status() {
            return new DiscordStatus(DiscordStatus.State.READY, null, 0, Map.of());
        }

        @Override
        public void send(OutboundMessage message) {
            sent.add(message);
        }

        @Override
        public void updatePresence(String text) {}

        @Override
        public void updateTopic(long channelId, String topic) {}

        @Override
        public boolean isApplicationOwner(long userId) {
            return false;
        }

        @Override
        public void shutdown(List<OutboundMessage> finalMessages) {}
    }
}
