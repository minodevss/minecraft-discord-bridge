package com.minecraftdiscordbridge.core;

import static com.minecraftdiscordbridge.core.Harness.CHANNELS;
import static com.minecraftdiscordbridge.core.Harness.CHAT;
import static com.minecraftdiscordbridge.core.Harness.STEVE;
import static com.minecraftdiscordbridge.core.Harness.TOKEN;
import static org.assertj.core.api.Assertions.assertThat;

import com.minecraftdiscordbridge.core.discord.DiscordStatus;
import com.minecraftdiscordbridge.core.fakes.RecordingDiscord;
import com.minecraftdiscordbridge.core.fakes.RecordingReply;
import com.minecraftdiscordbridge.core.fakes.RecordingSource;
import com.minecraftdiscordbridge.core.fakes.Waits;
import com.minecraftdiscordbridge.core.platform.GameCommand;
import com.minecraftdiscordbridge.core.platform.GameEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReloadTest {
    @TempDir
    Path directory;

    @Test
    void staysIdleWithABrokenConfigAndStartsAfterAFixAndReload() throws IOException {
        try (Harness harness = Harness.create(directory, Harness.withEventSettings("  join: maybe\n"))) {
            harness.bridge.start();
            assertThat(harness.settledStatus()).contains("The settings have errors");
            assertThat(harness.discords).isEmpty();

            harness.writeConfig(CHANNELS);
            RecordingSource console = harness.run(RecordingSource.console(), GameCommand.RELOAD);

            assertThat(console.nextPlain()).isEqualTo("Reloaded config.yml and secrets.yml. Reconnecting to Discord.");
            assertThat(harness.discord().started).isTrue();
        }
    }

    @Test
    void asksToTryAgainWhileTheBridgeIsStillStarting() throws IOException {
        try (Harness harness = Harness.create(directory, CHANNELS)) {
            RecordingSource console = harness.run(RecordingSource.console(), GameCommand.RELOAD);

            assertThat(console.nextPlain()).isEqualTo("The bridge is still starting. Try again in a moment.");
        }
    }

    @Test
    void keepsTheOldSettingsWhenAReloadedConfigIsBroken() throws IOException {
        try (Harness harness = Harness.started(directory, CHANNELS)) {
            harness.writeConfig(Harness.withEventSettings("  join: maybe\n"));
            RecordingSource console = harness.run(RecordingSource.console(), GameCommand.RELOAD);

            assertThat(console.nextPlain()).contains("old settings stay in use");
            assertThat(console.nextPlain()).contains("events.join: expected true or false");
            assertThat(console.nextPlain()).isEqualTo("Fix them and run /discord reload.");
            harness.publish(new GameEvent.PlayerJoined(STEVE, false));
            assertThat(harness.discord().nextSent().text()).isEqualTo("Steve joined the game");
            assertThat(harness.discords).hasSize(1);
        }
    }

    @Test
    void appliesFeatureChangesOnReloadWithoutReconnecting() throws IOException {
        try (Harness harness = Harness.started(directory, CHANNELS)) {
            harness.writeConfig(Harness.withEventSettings("  join: false\n"));
            RecordingSource console = harness.run(RecordingSource.console(), GameCommand.RELOAD);

            assertThat(console.nextPlain()).isEqualTo("Reloaded config.yml and secrets.yml.");
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

            RecordingSource console = harness.run(RecordingSource.console(), GameCommand.RELOAD);

            assertThat(console.nextPlain()).isEqualTo("Reloaded config.yml and secrets.yml. Reconnecting to Discord.");
            assertThat(harness.discord().settings.token()).isEqualTo(TOKEN);
            assertThat(harness.discord().started).isTrue();
        }
    }

    @Test
    void reconnectsOnReloadWhenDiscordStoppedEvenIfNothingChanged() throws IOException {
        try (Harness harness = Harness.started(directory, CHANNELS)) {
            harness.discord().state = DiscordStatus.State.STOPPED;

            RecordingSource console = harness.run(RecordingSource.console(), GameCommand.RELOAD);

            assertThat(console.nextPlain()).isEqualTo("Reloaded config.yml and secrets.yml. Reconnecting to Discord.");
            assertThat(harness.discord(2).started).isTrue();
        }
    }

    @Test
    void answersCommandsThatReachTheOldConnectionDuringAReconnect() throws IOException {
        Files.writeString(directory.resolve("secrets.yml"), "discord-token: \"first\"\n");
        try (Harness harness = Harness.create(directory, CHANNELS, Map.of())) {
            harness.bridge.start();
            RecordingDiscord first = harness.discord(1);
            Files.writeString(directory.resolve("secrets.yml"), "discord-token: \"second\"\n");
            harness.run(RecordingSource.console(), GameCommand.RELOAD);
            harness.discord(2);

            RecordingReply reply = first.command(Harness.request("list", null, Map.of(), Harness.MEMBER, CHAT));

            assertThat(reply.next())
                    .isEqualTo(new RecordingReply.Answer("The bridge is still starting. Try again in a moment.", true));
        }
    }

    @Test
    void connectsAgainOnlyAfterTheOldConnectionHasStopped() throws IOException {
        Files.writeString(directory.resolve("secrets.yml"), "discord-token: \"first\"\n");
        try (Harness harness = Harness.create(directory, CHANNELS, Map.of())) {
            harness.bridge.start();
            RecordingDiscord first = harness.discord(1);
            first.shutdownTime = Duration.ofMillis(500);
            Files.writeString(directory.resolve("secrets.yml"), "discord-token: \"second\"\n");

            harness.run(RecordingSource.console(), GameCommand.RELOAD);
            harness.run(RecordingSource.console(), GameCommand.RELOAD);

            harness.discord(2);
            assertThat(first.shutDown).isTrue();
        }
    }

    @Test
    void reconnectsOnReloadWhenTheTokenChanges() throws IOException {
        Files.writeString(directory.resolve("secrets.yml"), "discord-token: \"first\"\n");
        try (Harness harness = Harness.create(directory, CHANNELS, Map.of())) {
            harness.bridge.start();
            RecordingDiscord first = harness.discord(1);
            Files.writeString(directory.resolve("secrets.yml"), "discord-token: \"second\"\n");

            harness.run(RecordingSource.console(), GameCommand.RELOAD);

            RecordingDiscord second = harness.discord(2);
            assertThat(second.settings.token()).isEqualTo("second");
            assertThat(first.shutDown).isTrue();
        }
    }
}
