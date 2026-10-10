package com.minecraftdiscordbridge.core;

import static com.minecraftdiscordbridge.core.Harness.ALEX;
import static com.minecraftdiscordbridge.core.Harness.CHANNELS;
import static com.minecraftdiscordbridge.core.Harness.CHAT;
import static com.minecraftdiscordbridge.core.Harness.MEMBER;
import static com.minecraftdiscordbridge.core.Harness.STEVE;
import static org.assertj.core.api.Assertions.assertThat;

import com.minecraftdiscordbridge.core.platform.GameEvent;
import com.minecraftdiscordbridge.core.platform.PlayerProfile;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StatusTest {
    @TempDir
    Path directory;

    @Test
    void showsThePlayerCountWithoutVanishedPlayers() throws IOException {
        try (Harness harness = Harness.started(directory, CHANNELS)) {
            RecordingDiscord discord = harness.discord();
            harness.publish(new GameEvent.ServerStarted(List.of(STEVE)));
            harness.publish(new GameEvent.PlayerJoined(ALEX, true));
            harness.publish(new GameEvent.PlayerLeft(STEVE, false));

            Waits.until("presence updates", () -> discord.presences.size() >= 4);
            assertThat(discord.presences.subList(discord.presences.size() - 3, discord.presences.size()))
                    .containsExactly("1 player online", "1 player online", "0 players online");
            assertThat(discord.topics).isEmpty();
        }
    }

    @Test
    void updatesTheTopicWhenAsked() throws IOException {
        try (Harness harness = Harness.started(directory, CHANNELS + """
                status:
                  presence: false
                  topic: true
                """)) {
            RecordingDiscord discord = harness.discord();
            harness.publish(new GameEvent.ServerStarted(List.of(STEVE, ALEX)));

            Waits.until("topic", () -> discord.topics.contains(CHAT + ":2 players online"));
            assertThat(discord.presences).isEmpty();
        }
    }

    @Test
    void countsPlayersInKorean() throws IOException {
        try (Harness harness = Harness.started(directory, "language: ko\n" + CHANNELS)) {
            RecordingDiscord discord = harness.discord();
            harness.publish(new GameEvent.ServerStarted(List.of(STEVE, ALEX)));

            Waits.until("presence", () -> discord.presences.contains("2명 접속 중"));
        }
    }

    @Test
    void listsOnlineVisiblePlayers() throws IOException {
        try (Harness harness = Harness.started(directory, CHANNELS)) {
            RecordingDiscord discord = harness.discord();
            assertThat(discord.command(Harness.request("list", null, Map.of(), MEMBER, CHAT))
                            .next())
                    .isEqualTo(new RecordingReply.Answer("Nobody is online.", false));

            harness.publish(new GameEvent.ServerStarted(List.of(STEVE)));
            harness.publish(new GameEvent.PlayerJoined(ALEX, false));
            harness.publish(new GameEvent.PlayerJoined(
                    new PlayerProfile(UUID.randomUUID(), "Ghost", "Ghost", null, false), true));
            Waits.until("roster", () -> discord.presences.contains("2 players online"));

            assertThat(discord.command(Harness.request("list", null, Map.of(), MEMBER, CHAT))
                            .next())
                    .isEqualTo(new RecordingReply.Answer("Online (2): Alex\\_2, Steve", false));
        }
    }

    @Test
    void reportsTheConnectionInGame() throws IOException {
        try (Harness harness = Harness.started(directory, CHANNELS)) {
            String status = harness.settledStatus();

            assertThat(status)
                    .contains("Discord: connected")
                    .contains("Gateway ping: 42 ms")
                    .contains("Linked accounts: 0");
        }
    }

    @Test
    void refusesStatusAndReloadWithoutPermission() throws IOException {
        try (Harness harness = Harness.started(directory, CHANNELS)) {
            RecordingSource player = RecordingSource.player(STEVE.id(), "Steve");

            assertThat(harness.run(player, "status").nextPlain()).isEqualTo("You do not have permission to do that.");
            assertThat(harness.run(player, "reload").nextPlain()).isEqualTo("You do not have permission to do that.");
        }
    }

    @Test
    void showsHelpForTheCommandsTheSourceMayUse() throws IOException {
        try (Harness harness = Harness.started(directory, CHANNELS)) {
            RecordingSource player = harness.run(RecordingSource.player(STEVE.id(), "Steve"));

            assertThat(player.nextPlain()).isEqualTo("Minecraft Discord Bridge");
            assertThat(player.nextPlain()).startsWith("/discord link");
            assertThat(player.nextPlain()).startsWith("/discord unlink");
            assertThat(player.nextPlain()).startsWith("/discord toggle");
            Waits.nothingIn(player.replies);
        }
    }
}
