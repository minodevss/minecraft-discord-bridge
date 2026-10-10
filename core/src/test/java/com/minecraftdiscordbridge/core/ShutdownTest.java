package com.minecraftdiscordbridge.core;

import static com.minecraftdiscordbridge.core.Harness.CHANNELS;
import static com.minecraftdiscordbridge.core.Harness.EVENTS;
import static com.minecraftdiscordbridge.core.Harness.STEVE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.minecraftdiscordbridge.core.discord.OutboundMessage;
import com.minecraftdiscordbridge.core.fakes.RecordingDiscord;
import com.minecraftdiscordbridge.core.fakes.RecordingSource;
import com.minecraftdiscordbridge.core.platform.GameCommand;
import com.minecraftdiscordbridge.core.platform.GameEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ShutdownTest {
    @TempDir
    Path directory;

    @Test
    void reportsRecoveryFromAnUncleanShutdownAndClearsTheMarkerOnStop() throws IOException {
        Path marker = directory.resolve("data").resolve("running");
        Files.createDirectories(marker.getParent());
        Files.writeString(marker, "");
        Harness harness = Harness.started(directory, CHANNELS);

        harness.publish(new GameEvent.ServerStarted(List.of()));

        assertThat(harness.discord().nextSent().text()).isEqualTo("The server has started after an unclean shutdown");
        harness.close();
        assertThat(marker).doesNotExist();
    }

    @Test
    void marksTheServerAsRunningUntilItStopsCleanly() throws IOException {
        Harness harness = Harness.started(directory, CHANNELS);
        harness.publish(new GameEvent.ServerStarted(List.of()));

        assertThat(harness.discord().nextSent().text()).isEqualTo("The server has started");
        assertThat(directory.resolve("data").resolve("running")).exists();
        harness.close();
    }

    @Test
    void sendsServerStoppedAsTheLastMessage() throws IOException {
        Harness harness = Harness.started(directory, CHANNELS);
        RecordingDiscord discord = harness.discord();

        harness.publish(new GameEvent.ServerStopping());
        harness.bridge.stop();

        assertThat(discord.finalMessages).extracting(OutboundMessage::text).containsExactly("The server has stopped");
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
                .containsExactly("The server has stopped unexpectedly");
    }

    @Test
    void skipsTheStopPostWhenItIsTurnedOff() throws IOException {
        Harness harness = Harness.started(directory, Harness.withEventSettings("  server-stop: false\n"));
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
                    harness.bridge.command(RecordingSource.console(), GameCommand.STATUS);
                })
                .doesNotThrowAnyException();
    }
}
