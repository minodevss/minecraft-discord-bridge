package com.minecraftdiscordbridge.core;

import static com.minecraftdiscordbridge.core.Harness.CHANNELS;
import static com.minecraftdiscordbridge.core.Harness.STEVE;
import static org.assertj.core.api.Assertions.assertThat;

import com.minecraftdiscordbridge.core.fakes.RecordingSource;
import com.minecraftdiscordbridge.core.fakes.Waits;
import com.minecraftdiscordbridge.core.platform.GameCommand;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GameCommandTest {
    private static final String NO_PERMISSION = "You do not have permission to do that.";

    @TempDir
    Path directory;

    @Test
    void letsPlayersUseOnlyThePlayerCommandsTheyHavePermissionFor() throws IOException {
        try (Harness harness = Harness.started(directory, CHANNELS)) {
            RecordingSource player = RecordingSource.player(STEVE.id(), "Steve");

            assertThat(usable(harness, player))
                    .containsExactly(GameCommand.LINK, GameCommand.UNLINK, GameCommand.TOGGLE);
        }
    }

    @Test
    void keepsSetupForTheConsoleAndPlayerCommandsForPlayers() throws IOException {
        try (Harness harness = Harness.started(directory, CHANNELS)) {
            assertThat(usable(harness, RecordingSource.console()))
                    .containsExactly(GameCommand.STATUS, GameCommand.RELOAD, GameCommand.SETUP);
            assertThat(usable(harness, RecordingSource.operator(STEVE.id(), "Steve")))
                    .containsExactly(
                            GameCommand.LINK,
                            GameCommand.UNLINK,
                            GameCommand.TOGGLE,
                            GameCommand.STATUS,
                            GameCommand.RELOAD);
        }
    }

    @Test
    void hidesLinkAndUnlinkWhileLinkingIsOff() throws IOException {
        try (Harness harness = Harness.started(directory, CHANNELS + "linking:\n  enabled: false\n")) {
            Waits.until(
                    "linking to turn off",
                    () -> !harness.bridge.canUse(RecordingSource.player(STEVE.id(), "Steve"), GameCommand.LINK));

            assertThat(usable(harness, RecordingSource.player(STEVE.id(), "Steve")))
                    .containsExactly(GameCommand.TOGGLE);
            RecordingSource player = harness.help(RecordingSource.player(STEVE.id(), "Steve"));
            assertThat(player.nextPlain()).isEqualTo("Minecraft Discord Bridge");
            assertThat(player.nextPlain()).startsWith("/discord toggle");
            Waits.nothingIn(player.replies);
        }
    }

    @Test
    void listsOnlyTheCommandsAPlayerMayUseInHelp() throws IOException {
        try (Harness harness = Harness.started(directory, CHANNELS)) {
            RecordingSource player = harness.help(RecordingSource.player(STEVE.id(), "Steve"));

            assertThat(player.nextPlain()).isEqualTo("Minecraft Discord Bridge");
            assertThat(player.nextPlain()).startsWith("/discord link");
            assertThat(player.nextPlain()).startsWith("/discord unlink");
            assertThat(player.nextPlain()).startsWith("/discord toggle");
            Waits.nothingIn(player.replies);
        }
    }

    @Test
    void listsOnlyTheCommandsTheConsoleMayUseInHelp() throws IOException {
        try (Harness harness = Harness.started(directory, CHANNELS)) {
            RecordingSource console = harness.help(RecordingSource.console());

            assertThat(console.nextPlain()).isEqualTo("Minecraft Discord Bridge");
            assertThat(console.nextPlain()).startsWith("/discord status");
            assertThat(console.nextPlain()).startsWith("/discord reload");
            assertThat(console.nextPlain()).startsWith("/discord setup");
            Waits.nothingIn(console.replies);
        }
    }

    @Test
    void refusesCommandsWithoutPermission() throws IOException {
        try (Harness harness = Harness.started(directory, CHANNELS)) {
            RecordingSource player = RecordingSource.player(STEVE.id(), "Steve");

            assertThat(harness.run(player, GameCommand.STATUS).nextPlain()).isEqualTo(NO_PERMISSION);
            assertThat(harness.run(player, GameCommand.RELOAD).nextPlain()).isEqualTo(NO_PERMISSION);
        }
    }

    @Test
    void refusesCommandsFromTheWrongKindOfSender() throws IOException {
        try (Harness harness = Harness.started(directory, CHANNELS)) {
            assertThat(harness.run(RecordingSource.console(), GameCommand.LINK).nextPlain())
                    .isEqualTo(NO_PERMISSION);
            assertThat(harness.run(RecordingSource.console(), GameCommand.TOGGLE)
                            .nextPlain())
                    .isEqualTo(NO_PERMISSION);
            assertThat(harness.run(RecordingSource.operator(STEVE.id(), "Steve"), GameCommand.SETUP)
                            .nextPlain())
                    .isEqualTo(NO_PERMISSION);
        }
    }

    private static GameCommand[] usable(Harness harness, RecordingSource source) {
        return Arrays.stream(GameCommand.values())
                .filter(command -> harness.bridge.canUse(source, command))
                .toArray(GameCommand[]::new);
    }
}
