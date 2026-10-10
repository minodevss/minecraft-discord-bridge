package com.minecraftdiscordbridge.core;

import static com.minecraftdiscordbridge.core.Harness.CHANNELS;
import static com.minecraftdiscordbridge.core.Harness.CHAT;
import static com.minecraftdiscordbridge.core.Harness.EVENTS;
import static com.minecraftdiscordbridge.core.Harness.STEVE;
import static com.minecraftdiscordbridge.core.Harness.TOKEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.minecraftdiscordbridge.core.discord.CommandSpec;
import com.minecraftdiscordbridge.core.discord.DiscordSettings;
import com.minecraftdiscordbridge.core.discord.DiscordStatus;
import com.minecraftdiscordbridge.core.discord.OutboundMessage;
import com.minecraftdiscordbridge.core.platform.GameEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.event.Level;

class BridgeLifecycleTest {
    @TempDir
    Path directory;

    @Test
    void connectsWithTheConfiguredChannelsAndCommands() throws IOException {
        try (Harness harness = Harness.started(directory, CHANNELS)) {
            DiscordSettings settings = harness.discord().settings;

            assertThat(settings.token()).isEqualTo(TOKEN);
            assertThat(settings.guildId()).isEqualTo(DiscordSettings.NO_GUILD);
            assertThat(settings.channelIds()).containsExactlyInAnyOrder(CHAT, EVENTS);
            assertThat(settings.requestMembersIntent()).isFalse();
            assertThat(commandNames(settings)).containsExactly("setup", "list", "link", "unlink");
            assertThat(harness.discord().started).isTrue();
        }
    }

    @Test
    void offersOnlySetupUntilAChannelIsChosen() throws IOException {
        try (Harness harness = Harness.started(directory, "config-version: 1\n")) {
            DiscordSettings settings = harness.discord().settings;

            assertThat(settings.channelIds()).isEmpty();
            assertThat(commandNames(settings)).containsExactly("setup");
        }
    }

    @Test
    void asksForTheMembersIntentOnlyWhenARoleIsRequired() throws IOException {
        try (Harness harness = Harness.started(directory, CHANNELS + """
                linking:
                  required-role: "777777777777777777"
                """)) {
            assertThat(harness.discord().settings.requestMembersIntent()).isTrue();
        }
    }

    @Test
    void staysIdleWithoutATokenAndKeepsTheServerUnaffected() throws IOException {
        try (Harness harness = Harness.create(directory, CHANNELS, Map.of())) {
            harness.bridge.start();

            assertThat(harness.settledStatus())
                    .startsWith("Discord: not connected")
                    .contains("No bot token is set");
            assertThatCode(() -> harness.publish(new GameEvent.PlayerChatted(STEVE, "hi")))
                    .doesNotThrowAnyException();
            assertThat(harness.discords).isEmpty();
        }
    }

    @Test
    void staysIdleWithABrokenConfigAndStartsAfterAFixAndReload() throws IOException {
        try (Harness harness = Harness.create(directory, CHANNELS + "events:\n  join: maybe\n")) {
            harness.bridge.start();
            assertThat(harness.settledStatus()).contains("The settings have errors");
            assertThat(harness.discords).isEmpty();

            harness.writeConfig(CHANNELS);
            RecordingSource console = harness.run(RecordingSource.console(), "reload");

            assertThat(console.nextPlain()).isEqualTo("Reloaded config.yml. Reconnecting to Discord.");
            assertThat(harness.discord().started).isTrue();
        }
    }

    @Test
    void keepsTheOldSettingsWhenAReloadedConfigIsBroken() throws IOException {
        try (Harness harness = Harness.started(directory, CHANNELS)) {
            harness.writeConfig(CHANNELS + "events:\n  join: maybe\n");
            RecordingSource console = harness.run(RecordingSource.console(), "reload");

            assertThat(console.nextPlain()).contains("old settings stay in use");
            assertThat(console.nextPlain()).contains("events.join: expected true or false");
            assertThat(console.nextPlain()).isEqualTo("Fix config.yml and run /discord reload.");
            harness.publish(new GameEvent.PlayerJoined(STEVE, false));
            assertThat(harness.discord().nextSent().text()).isEqualTo("**Steve** joined the server");
            assertThat(harness.discords).hasSize(1);
        }
    }

    @Test
    void appliesFeatureChangesOnReloadWithoutReconnecting() throws IOException {
        try (Harness harness = Harness.started(directory, CHANNELS)) {
            harness.writeConfig(CHANNELS + "events:\n  join: false\n");
            RecordingSource console = harness.run(RecordingSource.console(), "reload");

            assertThat(console.nextPlain()).isEqualTo("Reloaded config.yml.");
            harness.publish(new GameEvent.PlayerJoined(STEVE, false));
            Waits.nothingIn(harness.discord().sent);
            assertThat(harness.discords).hasSize(1);
        }
    }

    @Test
    void connectsAfterATokenIsPutInSecretsYmlAndTheConfigIsReloaded() throws IOException {
        try (Harness harness = Harness.create(directory, CHANNELS, Map.of())) {
            harness.bridge.start();
            assertThat(harness.settledStatus()).contains("No bot token is set");
            Files.writeString(directory.resolve("secrets.yml"), "discord-token: \"" + TOKEN + "\"\n");

            RecordingSource console = harness.run(RecordingSource.console(), "reload");

            assertThat(console.nextPlain()).isEqualTo("Reloaded config.yml. Reconnecting to Discord.");
            assertThat(harness.discord().settings.token()).isEqualTo(TOKEN);
            assertThat(harness.discord().started).isTrue();
        }
    }

    @Test
    void explainsTheMissingTokenInAShortBannerThatAgreesWithItself() throws IOException {
        try (RecordingLogs.Capture logs = RecordingLogs.capture();
                Harness harness = Harness.create(directory, CHANNELS, Map.of())) {
            harness.bridge.start();
            harness.settledStatus();

            assertThat(logs.lines(Level.WARN))
                    .hasSizeLessThanOrEqualTo(3)
                    .anySatisfy(line -> assertThat(line).contains(SetupGuide.QUICK_START))
                    .anySatisfy(line -> assertThat(line)
                            .contains("https://discord.com/developers/applications")
                            .contains(directory.resolve("secrets.yml").toString())
                            .contains("/discord reload"));
            assertThat(logs.lines()).noneSatisfy(line -> assertThat(line).contains("restart"));
        }
    }

    @Test
    void reconnectsOnReloadWhenDiscordStoppedEvenIfNothingChanged() throws IOException {
        try (Harness harness = Harness.started(directory, CHANNELS)) {
            harness.discord().state = DiscordStatus.State.STOPPED;

            RecordingSource console = harness.run(RecordingSource.console(), "reload");

            assertThat(console.nextPlain()).isEqualTo("Reloaded config.yml. Reconnecting to Discord.");
            assertThat(harness.discord(2).started).isTrue();
        }
    }

    @Test
    void reconnectsOnReloadWhenTheTokenChanges() throws IOException {
        Files.writeString(directory.resolve("secrets.yml"), "discord-token: \"first\"\n");
        try (Harness harness = Harness.create(directory, CHANNELS, Map.of())) {
            harness.bridge.start();
            RecordingDiscord first = harness.discord(1);
            Files.writeString(directory.resolve("secrets.yml"), "discord-token: \"second\"\n");

            harness.run(RecordingSource.console(), "reload");

            RecordingDiscord second = harness.discord(2);
            assertThat(second.settings.token()).isEqualTo("second");
            assertThat(first.shutDown).isTrue();
        }
    }

    @Test
    void mirrorsTheConsoleOnlyWhileABotTokenIsSet() throws IOException {
        String mirrored = """
                config-version: 1
                discord:
                  chat-channel: "222222222222222222"
                  console-channel: "444444444444444444"
                console:
                  mirror: true
                """;
        Files.writeString(directory.resolve("secrets.yml"), "discord-token: \"" + TOKEN + "\"\n");
        try (Harness harness = Harness.create(directory, mirrored, Map.of())) {
            harness.bridge.start();
            harness.discord();
            assertThat(consoleMirrorAttached()).isTrue();

            Files.writeString(directory.resolve("secrets.yml"), "discord-token: \"\"\n");
            assertThat(harness.run(RecordingSource.console(), "reload").nextPlain())
                    .contains("no bot token is set");

            assertThat(consoleMirrorAttached()).isFalse();
        }
    }

    @Test
    void reportsRecoveryFromAnUncleanShutdownAndClearsTheMarkerOnStop() throws IOException {
        Path marker = directory.resolve("data").resolve("running");
        Files.createDirectories(marker.getParent());
        Files.writeString(marker, "");
        Harness harness = Harness.started(directory, CHANNELS);

        harness.publish(new GameEvent.ServerStarted(List.of()));

        assertThat(harness.discord().nextSent().text())
                .isEqualTo("The server has started. It recovered from an unclean shutdown.");
        harness.close();
        assertThat(marker).doesNotExist();
    }

    @Test
    void marksTheServerAsRunningUntilItStopsCleanly() throws IOException {
        Harness harness = Harness.started(directory, CHANNELS);
        harness.publish(new GameEvent.ServerStarted(List.of()));

        assertThat(harness.discord().nextSent().text()).isEqualTo("The server has started.");
        assertThat(directory.resolve("data").resolve("running")).exists();
        harness.close();
    }

    @Test
    void sendsServerStoppedAsTheLastMessage() throws IOException {
        Harness harness = Harness.started(directory, CHANNELS);
        RecordingDiscord discord = harness.discord();

        harness.publish(new GameEvent.ServerStopping());
        harness.bridge.stop();

        assertThat(discord.finalMessages).extracting(OutboundMessage::text).containsExactly("The server has stopped.");
        assertThat(discord.finalMessages.get(0).channelId()).isEqualTo(EVENTS);
        assertThat(discord.shutDown).isTrue();
    }

    @Test
    void treatsServerStoppedWithoutStoppingAsACrash() throws IOException {
        Harness harness = Harness.started(directory, CHANNELS);
        RecordingDiscord discord = harness.discord();

        harness.publish(new GameEvent.ServerStopped());
        harness.bridge.stop();

        assertThat(discord.finalMessages)
                .extracting(OutboundMessage::text)
                .containsExactly("The server has stopped unexpectedly.");
    }

    @Test
    void skipsTheStopPostWhenItIsTurnedOff() throws IOException {
        Harness harness = Harness.started(directory, CHANNELS + "events:\n  server-stop: false\n");
        RecordingDiscord discord = harness.discord();

        harness.publish(new GameEvent.ServerStopping());
        harness.bridge.stop();

        assertThat(discord.finalMessages).isEmpty();
        assertThat(discord.shutDown).isTrue();
    }

    @Test
    void stopsWithinThreeSecondsWhenDiscordHangs() throws IOException {
        Harness harness = Harness.started(directory, CHANNELS);
        harness.discord().shutdownTime = Duration.ofSeconds(10);

        long started = System.nanoTime();
        harness.bridge.stop();

        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThanOrEqualTo(Duration.ofSeconds(3));
    }

    @Test
    void stopsSafelyBeforeStartHasFinishedAndOnlyOnce() throws IOException {
        Harness harness = Harness.create(directory, CHANNELS);

        harness.bridge.start();

        assertThatCode(() -> {
                    harness.bridge.stop();
                    harness.bridge.stop();
                    harness.bridge.publish(new GameEvent.PlayerChatted(STEVE, "late"));
                    harness.bridge.command(RecordingSource.console(), List.of("status"));
                })
                .doesNotThrowAnyException();
    }

    private static boolean consoleMirrorAttached() {
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        return context.getConfiguration().getRootLogger().getAppenders().containsKey("MinecraftDiscordBridgeConsole");
    }

    private static List<String> commandNames(DiscordSettings settings) {
        return settings.commands().stream().map(CommandSpec::name).toList();
    }
}
