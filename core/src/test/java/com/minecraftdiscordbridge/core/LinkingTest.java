package com.minecraftdiscordbridge.core;

import static com.minecraftdiscordbridge.core.Harness.CHANNELS;
import static com.minecraftdiscordbridge.core.Harness.CHAT;
import static com.minecraftdiscordbridge.core.Harness.MEMBER;
import static com.minecraftdiscordbridge.core.Harness.STEVE;
import static org.assertj.core.api.Assertions.assertThat;

import com.minecraftdiscordbridge.core.discord.CommandSpec;
import com.minecraftdiscordbridge.core.discord.DiscordId;
import com.minecraftdiscordbridge.core.platform.GameEvent;
import com.minecraftdiscordbridge.core.storage.Edition;
import com.minecraftdiscordbridge.core.storage.Link;
import com.minecraftdiscordbridge.core.storage.LinkStore;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LinkingTest {
    @TempDir
    Path directory;

    @Test
    void givesLinkCodesWhenOnlyTheConsoleChannelIsSet() throws IOException {
        try (Harness harness = Harness.started(directory, """
                config-version: 1
                discord:
                  console-channel: "444444444444444444"
                console:
                  input-users: ["555555555555555555"]
                """)) {
            String reply = harness.run(RecordingSource.player(STEVE.id(), "Steve"), "link")
                    .nextPlain();

            assertThat(reply).startsWith("Run /link ");
        }
    }

    @Test
    void linksAccountsWithACodeFromMinecraft() throws IOException {
        try (Harness harness = Harness.started(directory, CHANNELS)) {
            harness.publish(new GameEvent.PlayerJoined(STEVE, false));
            RecordingSource steve = RecordingSource.player(STEVE.id(), "Steve");
            String reply = harness.run(steve, "link").nextPlain();
            assertThat(reply).startsWith("Run /link ").contains(" in Discord within 5 minutes");

            RecordingReply linked = link(harness, MEMBER, Harness.code(reply));

            assertThat(linked.next())
                    .isEqualTo(new RecordingReply.Answer("Linked to the Minecraft account **Steve**.", true));
            assertThat(steve.nextPlain()).isEqualTo("Your account is now linked to the Discord account Alex.");
            Link stored = LinkStore.open(directory).byMinecraftId(STEVE.id()).orElseThrow();
            assertThat(stored.discordId()).isEqualTo(new DiscordId(MEMBER));
            assertThat(stored.minecraftName()).isEqualTo("Steve");
            assertThat(stored.edition()).isEqualTo(Edition.JAVA);
            assertThat(harness.run(steve, "link").nextPlain()).contains("already linked");
        }
    }

    @Test
    void acceptsEachCodeOnceAndOnlyTheNewestPerPlayer() throws IOException {
        try (Harness harness = Harness.started(directory, CHANNELS)) {
            RecordingSource steve = RecordingSource.player(STEVE.id(), "Steve");
            String old = Harness.code(harness.run(steve, "link").nextPlain());
            String fresh = Harness.code(harness.run(steve, "link").nextPlain());

            assertThat(link(harness, MEMBER, old).next().text()).contains("wrong or expired");
            assertThat(link(harness, MEMBER, fresh).next().text()).startsWith("Linked");
            assertThat(link(harness, MEMBER + 1, fresh).next().text()).contains("wrong or expired");
        }
    }

    @Test
    void expiresCodesAfterFiveMinutes() throws IOException {
        try (Harness harness = Harness.started(directory, CHANNELS)) {
            String code = Harness.code(harness.run(RecordingSource.player(STEVE.id(), "Steve"), "link")
                    .nextPlain());
            harness.clock.advance(Duration.ofMinutes(5));

            assertThat(link(harness, MEMBER, code).next().text()).contains("wrong or expired");
        }
    }

    @Test
    void locksOutAUserAfterFiveWrongCodesForTenMinutes() throws IOException {
        try (Harness harness = Harness.started(directory, CHANNELS)) {
            for (int i = 0; i < 5; i++) {
                assertThat(link(harness, MEMBER, "WRONG" + i).next().text()).contains("wrong or expired");
            }
            String code = Harness.code(harness.run(RecordingSource.player(STEVE.id(), "Steve"), "link")
                    .nextPlain());

            assertThat(link(harness, MEMBER, code).next().text())
                    .isEqualTo("Too many wrong codes. Try again in a few minutes.");
            assertThat(link(harness, MEMBER + 1, code).next().text()).startsWith("Linked");
        }
    }

    @Test
    void refusesEveryoneForAWhileAfterManyWrongCodes() throws IOException {
        try (Harness harness = Harness.started(directory, CHANNELS)) {
            for (int i = 0; i < 50; i++) {
                link(harness, MEMBER + i, "WRONG").next();
            }
            String code = Harness.code(harness.run(RecordingSource.player(STEVE.id(), "Steve"), "link")
                    .nextPlain());

            assertThat(link(harness, MEMBER + 100, code).next().text()).startsWith("Too many wrong codes");
            harness.clock.advance(Duration.ofMinutes(10).plusSeconds(1));
            code = Harness.code(harness.run(RecordingSource.player(STEVE.id(), "Steve"), "link")
                    .nextPlain());
            assertThat(link(harness, MEMBER + 100, code).next().text()).startsWith("Linked");
        }
    }

    @Test
    void unlinksFromDiscordAndFromMinecraft() throws IOException {
        LinkStore.open(directory)
                .link(new Link(STEVE.id(), new DiscordId(MEMBER), "Steve", Edition.JAVA, Instant.EPOCH));
        try (Harness harness = Harness.started(directory, CHANNELS)) {
            RecordingReply unlinked =
                    harness.discord().command(Harness.request("unlink", null, Map.of(), MEMBER, CHAT));
            assertThat(unlinked.next().text()).isEqualTo("Unlinked from the Minecraft account **Steve**.");
            assertThat(harness.discord()
                            .command(Harness.request("unlink", null, Map.of(), MEMBER, CHAT))
                            .next()
                            .text())
                    .isEqualTo("Your Discord account is not linked.");
            RecordingSource steve = RecordingSource.player(STEVE.id(), "Steve");
            assertThat(harness.run(steve, "unlink").nextPlain()).isEqualTo("Your account is not linked to Discord.");
            assertThat(LinkStore.open(directory).all()).isEmpty();
        }
    }

    @Test
    void offersNoLinkingWhenItIsTurnedOff() throws IOException {
        try (Harness harness = Harness.started(directory, CHANNELS + "linking:\n  enabled: false\n")) {
            assertThat(harness.discord().settings.commands())
                    .extracting(CommandSpec::name)
                    .doesNotContain("link");
            assertThat(harness.run(RecordingSource.player(STEVE.id(), "Steve"), "link")
                            .nextPlain())
                    .isEqualTo("Account linking is turned off on this server.");
            assertThat(link(harness, MEMBER, "ABCDEFGH").next().text())
                    .isEqualTo("Account linking is turned off on this server.");
        }
    }

    @Test
    void givesNoCodeWhileDiscordIsNotConnected() throws IOException {
        try (Harness harness = Harness.create(directory, CHANNELS, Map.of())) {
            harness.bridge.start();
            harness.settledStatus();

            assertThat(harness.run(RecordingSource.player(STEVE.id(), "Steve"), "link")
                            .nextPlain())
                    .isEqualTo("Discord is not connected right now. Try again later.");
        }
    }

    @Test
    void givesNoCodeUntilTheBotHasAChannel() throws IOException {
        try (Harness harness = Harness.started(directory, "config-version: 1\n")) {
            assertThat(harness.run(RecordingSource.player(STEVE.id(), "Steve"), "link")
                            .nextPlain())
                    .isEqualTo("Discord is not connected right now. Try again later.");
        }
    }

    @Test
    void onlyPlayersCanLink() throws IOException {
        try (Harness harness = Harness.started(directory, CHANNELS)) {
            assertThat(harness.run(RecordingSource.console(), "link").nextPlain())
                    .isEqualTo("Only players can do that.");
        }
    }

    private static RecordingReply link(Harness harness, long user, String code) {
        return harness.discord().command(Harness.request("link", null, Map.of("code", code), user, CHAT));
    }
}
