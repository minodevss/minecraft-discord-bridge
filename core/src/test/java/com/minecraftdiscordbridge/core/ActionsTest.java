package com.minecraftdiscordbridge.core;

import static com.minecraftdiscordbridge.core.Harness.CHANNELS;
import static com.minecraftdiscordbridge.core.Harness.CHAT;
import static com.minecraftdiscordbridge.core.Harness.MEMBER;
import static com.minecraftdiscordbridge.core.Harness.STEVE;
import static org.assertj.core.api.Assertions.assertThat;

import com.minecraftdiscordbridge.core.discord.AutocompleteReply;
import com.minecraftdiscordbridge.core.discord.AutocompleteRequest;
import com.minecraftdiscordbridge.core.discord.CommandRequest;
import com.minecraftdiscordbridge.core.discord.CommandSpec;
import com.minecraftdiscordbridge.core.fakes.FakePlatform;
import com.minecraftdiscordbridge.core.fakes.RecordingLogs;
import com.minecraftdiscordbridge.core.fakes.RecordingReply;
import com.minecraftdiscordbridge.core.fakes.Waits;
import com.minecraftdiscordbridge.core.platform.ActionOutcome;
import com.minecraftdiscordbridge.core.platform.GameAction;
import com.minecraftdiscordbridge.core.platform.GameEvent;
import com.minecraftdiscordbridge.core.platform.PlatformInfo;
import com.minecraftdiscordbridge.core.platform.PlayerProfile;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.event.Level;

class ActionsTest {
    private static final String ACTIONS_ON = CHANNELS + """
            discord-commands:
              whitelist: true
              kick: true
            """;

    @TempDir
    Path directory;

    @Test
    void offersNoWhitelistWhereThePlatformHasNoneAndSaysSo() throws IOException {
        try (RecordingLogs.Capture logs = RecordingLogs.capture();
                Harness harness = Harness.create(directory, ACTIONS_ON)) {
            harness.platform.info =
                    new PlatformInfo("Velocity 3.4.0", "1.21.1", FakePlatform.DATA_VERSION, true, false, false);
            harness.bridge.start();

            assertThat(harness.discord().settings.commands())
                    .extracting(CommandSpec::name)
                    .contains("kick")
                    .doesNotContain("whitelist");
            assertThat(logs.lines(Level.WARN))
                    .filteredOn(line -> line.contains("whitelist"))
                    .singleElement()
                    .asString()
                    .contains("has no effect on Velocity 3.4.0");
        }
    }

    @Test
    void offersWhitelistAndKickOnlyWhenTurnedOnAndOnlyToAdmins() throws IOException {
        try (Harness off = Harness.started(directory.resolve("off"), CHANNELS)) {
            assertThat(off.discord().settings.commands())
                    .extracting(CommandSpec::name)
                    .doesNotContain("whitelist", "kick");
        }
        try (Harness on = Harness.started(directory.resolve("on"), ACTIONS_ON)) {
            List<CommandSpec> commands = on.discord().settings.commands();
            assertThat(commands)
                    .filteredOn(command -> command.access() == CommandSpec.Access.ADMINS)
                    .extracting(CommandSpec::name)
                    .containsExactly("whitelist", "kick");
            CommandSpec whitelist = commands.stream()
                    .filter(command -> command.name().equals("whitelist"))
                    .findFirst()
                    .orElseThrow();
            assertThat(whitelist.subcommands())
                    .extracting(CommandSpec.Subcommand::name)
                    .containsExactly("add", "remove");
        }
    }

    @Test
    void refusesActionsThatAreTurnedOff() throws IOException {
        try (Harness harness = Harness.started(directory, CHANNELS)) {
            RecordingReply reply = harness.discord().command(whitelist("add", "Steve"));

            assertThat(reply.next().text()).isEqualTo("This action is turned off in config.yml.");
            assertThat(harness.platform.actions).isEmpty();
        }
    }

    @Test
    void whitelistsThroughThePlatformAndRepliesWithTheOutcome() throws IOException {
        try (Harness harness = Harness.started(directory, ACTIONS_ON)) {
            assertThat(harness.discord().command(whitelist("add", "Steve")).next())
                    .isEqualTo(new RecordingReply.Answer("Added **Steve** to the whitelist.", false));
            harness.platform.outcomes = action -> CompletableFuture.completedFuture(new ActionOutcome.Unchanged());
            assertThat(harness.discord()
                            .command(whitelist("remove", ".Bedrock_1"))
                            .next()
                            .text())
                    .isEqualTo("**.Bedrock\\_1** is not on the whitelist.");

            assertThat(harness.platform.actions)
                    .containsExactly(
                            new GameAction.WhitelistAdd("Steve"), new GameAction.WhitelistRemove(".Bedrock_1"));
        }
    }

    @Test
    void rejectsNamesThatCannotBeMinecraftNames() throws IOException {
        try (Harness harness = Harness.started(directory, ACTIONS_ON)) {
            for (String name : List.of("", "has space", "<@123>", "SeventeenCharsXYZ", "../../x")) {
                assertThat(harness.discord()
                                .command(whitelist("add", name))
                                .next()
                                .text())
                        .contains("is not a Minecraft name");
            }

            assertThat(harness.platform.actions).isEmpty();
        }
    }

    @Test
    void kicksWithTheGivenOrADefaultReason() throws IOException {
        try (Harness harness = Harness.started(directory, ACTIONS_ON)) {
            harness.platform.outcomes = action -> CompletableFuture.supplyAsync(ActionOutcome.Done::new);
            assertThat(harness.discord()
                            .command(kick(Map.of("player", "Steve", "reason", "spam")))
                            .next()
                            .text())
                    .isEqualTo("Kicked **Steve**.");
            harness.discord().command(kick(Map.of("player", "Steve"))).next();

            assertThat(harness.platform.actions)
                    .containsExactly(
                            new GameAction.Kick("Steve", "spam"),
                            new GameAction.Kick("Steve", "Kicked from Discord by Alex"));
        }
    }

    @Test
    void reportsPlayersWhoAreNotThereAndFailures() throws IOException {
        try (Harness harness = Harness.started(directory, ACTIONS_ON)) {
            harness.platform.outcomes =
                    action -> CompletableFuture.completedFuture(new ActionOutcome.NoSuchPlayer("Ghost"));
            assertThat(harness.discord()
                            .command(kick(Map.of("player", "Ghost")))
                            .next()
                            .text())
                    .isEqualTo("**Ghost** is not online.");
            harness.platform.outcomes = action -> CompletableFuture.failedFuture(new IllegalStateException("boom"));
            assertThat(harness.discord()
                            .command(whitelist("add", "Ghost"))
                            .next()
                            .text())
                    .isEqualTo("That did not work for **Ghost**: boom");
        }
    }

    @Test
    void answersEvenWhenThePlatformNeverDoes() throws IOException {
        try (Harness harness = Harness.started(directory, ACTIONS_ON)) {
            harness.platform.outcomes = action -> new CompletableFuture<>();

            assertThat(harness.discord()
                            .command(whitelist("add", "Steve"))
                            .next()
                            .text())
                    .isEqualTo("That did not work for **Steve**: the server did not answer in time");
        }
    }

    @Test
    void suggestsOnlinePlayersForKick() throws IOException {
        try (Harness harness = Harness.started(directory, ACTIONS_ON)) {
            harness.publish(new GameEvent.ServerStarted(
                    List.of(STEVE, new PlayerProfile(UUID.randomUUID(), "Stevie", "Stevie", null, false))));
            harness.publish(new GameEvent.PlayerJoined(
                    new PlayerProfile(UUID.randomUUID(), "Alex", "Alex", null, false), false));
            Waits.until("roster", () -> harness.discord().presences.contains("3 players online"));
            BlockingQueue<List<AutocompleteReply.Choice>> answers = new LinkedBlockingQueue<>();

            harness.discord()
                    .autocomplete(
                            new AutocompleteRequest("kick", "player", "ste", Harness.GUILD, CHAT, MEMBER, "en-US"),
                            answers::add);

            assertThat(Waits.take(answers))
                    .extracting(AutocompleteReply.Choice::value)
                    .containsExactly("Steve", "Stevie");
        }
    }

    private static CommandRequest whitelist(String change, String name) {
        return Harness.request("whitelist", change, Map.of("player", name), MEMBER, CHAT);
    }

    private static CommandRequest kick(Map<String, String> options) {
        return Harness.request("kick", null, options, MEMBER, CHAT);
    }
}
