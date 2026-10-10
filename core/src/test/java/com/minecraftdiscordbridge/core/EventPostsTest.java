package com.minecraftdiscordbridge.core;

import static com.minecraftdiscordbridge.core.Harness.ALEX;
import static com.minecraftdiscordbridge.core.Harness.CHANNELS;
import static com.minecraftdiscordbridge.core.Harness.CHAT;
import static com.minecraftdiscordbridge.core.Harness.EVENTS;
import static com.minecraftdiscordbridge.core.Harness.ROLE;
import static com.minecraftdiscordbridge.core.Harness.STEVE;
import static org.assertj.core.api.Assertions.assertThat;

import com.minecraftdiscordbridge.core.discord.Author;
import com.minecraftdiscordbridge.core.discord.OutboundMessage;
import com.minecraftdiscordbridge.core.platform.AdvancementFrame;
import com.minecraftdiscordbridge.core.platform.GameEvent;
import com.minecraftdiscordbridge.core.platform.RichText;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EventPostsTest {
    @TempDir
    Path directory;

    @Test
    void postsGameEventsToTheEventsChannelAsTheServer() throws IOException {
        try (Harness harness = Harness.started(directory, CHANNELS)) {
            harness.publish(new GameEvent.PlayerJoined(ALEX, false));
            harness.publish(new GameEvent.PlayerDied(ALEX, new RichText("{}", "Alex_2 fell from a high place")));
            harness.publish(new GameEvent.AdvancementEarned(ALEX, title("Stone Age"), AdvancementFrame.TASK));
            harness.publish(new GameEvent.AdvancementEarned(ALEX, title("Hot Stuff"), AdvancementFrame.GOAL));
            harness.publish(new GameEvent.AdvancementEarned(ALEX, title("Arbalistic"), AdvancementFrame.CHALLENGE));
            harness.publish(new GameEvent.PlayerLeft(ALEX, false));

            RecordingDiscord discord = harness.discord();
            List<OutboundMessage> posts = List.of(
                    discord.nextSent(),
                    discord.nextSent(),
                    discord.nextSent(),
                    discord.nextSent(),
                    discord.nextSent(),
                    discord.nextSent());
            assertThat(posts)
                    .extracting(OutboundMessage::text)
                    .containsExactly(
                            "**Alex\\_2** joined the server",
                            "Alex\\_2 fell from a high place",
                            "**Alex\\_2** has made the advancement **Stone Age**",
                            "**Alex\\_2** has reached the goal **Hot Stuff**",
                            "**Alex\\_2** has completed the challenge **Arbalistic**",
                            "**Alex\\_2** left the server");
            assertThat(posts).allSatisfy(post -> {
                assertThat(post.channelId()).isEqualTo(EVENTS);
                assertThat(post.author()).isEqualTo(Author.server());
                assertThat(post.pingUserIds()).isEmpty();
                assertThat(post.pingRoleIds()).isEmpty();
            });
        }
    }

    @Test
    void skipsVanishedPlayers() throws IOException {
        try (Harness harness = Harness.started(directory, CHANNELS)) {
            harness.publish(new GameEvent.PlayerJoined(STEVE, true));
            harness.publish(new GameEvent.PlayerLeft(STEVE, true));

            Waits.nothingIn(harness.discord().sent);
        }
    }

    @Test
    void respectsEachToggle() throws IOException {
        try (Harness harness = Harness.started(directory, CHANNELS + """
                events:
                  join: false
                  leave: false
                  death: false
                  advancement: false
                  server-start: false
                """)) {
            harness.publish(new GameEvent.ServerStarted(List.of()));
            harness.publish(new GameEvent.PlayerJoined(STEVE, false));
            harness.publish(new GameEvent.PlayerDied(STEVE, new RichText("{}", "Steve died")));
            harness.publish(new GameEvent.AdvancementEarned(STEVE, title("Stone Age"), AdvancementFrame.TASK));
            harness.publish(new GameEvent.PlayerSwitchedServer(STEVE, "lobby", "survival"));
            harness.publish(new GameEvent.PlayerLeft(STEVE, false));

            Waits.nothingIn(harness.discord().sent);
        }
    }

    @Test
    void usesTheChatChannelWhenNoEventsChannelIsSet() throws IOException {
        try (Harness harness = Harness.started(directory, """
                config-version: 1
                discord:
                  chat-channel: "222222222222222222"
                """)) {
            harness.publish(new GameEvent.PlayerJoined(STEVE, false));

            assertThat(harness.discord().nextSent().channelId()).isEqualTo(CHAT);
        }
    }

    @Test
    void pingsOnlyTheConfiguredRoleWhenTheServerStarts() throws IOException {
        try (Harness harness = Harness.started(directory, CHANNELS + """
                events:
                  server-start-ping-role: "777777777777777777"
                """)) {
            harness.publish(new GameEvent.ServerStarted(List.of()));

            OutboundMessage post = harness.discord().nextSent();
            assertThat(post.text()).isEqualTo("<@&" + ROLE + "> The server has started.");
            assertThat(post.pingRoleIds()).containsExactly(ROLE);
            assertThat(post.pingUserIds()).isEmpty();
        }
    }

    @Test
    void announcesTheStartOnlyOnceEvenAfterReloads() throws IOException {
        try (Harness harness = Harness.started(directory, CHANNELS)) {
            harness.publish(new GameEvent.ServerStarted(List.of()));
            assertThat(harness.discord().nextSent().text()).isEqualTo("The server has started.");

            harness.run(RecordingSource.console(), "reload").nextPlain();

            Waits.nothingIn(harness.discord().sent);
        }
    }

    @Test
    void writesPostsInKorean() throws IOException {
        try (Harness harness = Harness.started(directory, "language: ko\n" + CHANNELS)) {
            harness.publish(new GameEvent.AdvancementEarned(STEVE, title("Hot Stuff"), AdvancementFrame.GOAL));

            assertThat(harness.discord().nextSent().text()).isEqualTo("**Steve**님이 목표 **Hot Stuff**을(를) 달성했습니다");
        }
    }

    @Test
    void postsDeathsAndAdvancementsInMinecraftsOwnKorean() throws IOException {
        try (Harness harness = Harness.create(directory, "language: ko\n" + CHANNELS)) {
            harness.mojang.serve("1.21.1", "ko_kr", """
                    {"death.attack.fall": "%1$s이(가) 땅바닥으로 곤두박질쳤습니다",
                     "advancements.story.mine_stone.title": "석기 시대"}
                    """);
            harness.bridge.start();
            RecordingDiscord discord = harness.discord();
            GameEvent fell = new GameEvent.PlayerDied(
                    STEVE, new RichText("{\"translate\":\"death.attack.fall\",\"with\":[\"Steve\"]}", "Steve fell"));

            Waits.until("the Korean language file", () -> {
                harness.publish(fell);
                return discord.nextSent().text().equals("Steve이\\(가\\) 땅바닥으로 곤두박질쳤습니다");
            });
            harness.publish(new GameEvent.AdvancementEarned(
                    STEVE,
                    new RichText("{\"translate\":\"advancements.story.mine_stone.title\"}", "Stone Age"),
                    AdvancementFrame.TASK));
            harness.publish(new GameEvent.PlayerDied(
                    STEVE, new RichText("{\"translate\":\"death.attack.mymod.wisp\"}", "Steve met a wisp")));

            assertThat(discord.nextSent().text()).isEqualTo("**Steve**님이 발전 과제 **석기 시대**을(를) 달성했습니다");
            assertThat(discord.nextSent().text()).isEqualTo("Steve met a wisp");
        }
    }

    @Test
    void postsServerSwitchesOnAProxyWithoutRepeatingTheJoin() throws IOException {
        try (Harness harness = Harness.started(directory, CHANNELS)) {
            harness.publish(new GameEvent.PlayerJoined(ALEX, false));
            harness.publish(new GameEvent.PlayerSwitchedServer(ALEX, null, "lobby"));
            harness.publish(new GameEvent.PlayerSwitchedServer(ALEX, "lobby", "survival_1"));

            RecordingDiscord discord = harness.discord();
            assertThat(discord.nextSent().text()).isEqualTo("**Alex\\_2** joined the server");
            assertThat(discord.nextSent().text()).isEqualTo("**Alex\\_2** moved to **survival\\_1**");
            Waits.nothingIn(discord.sent);
        }
    }

    @Test
    void treatsTheFirstServerAsAJoinWhenNoJoinWasSeen() throws IOException {
        try (Harness harness = Harness.started(directory, CHANNELS)) {
            harness.publish(new GameEvent.PlayerSwitchedServer(STEVE, null, "lobby"));
            harness.publish(new GameEvent.PlayerSwitchedServer(STEVE, null, "lobby"));

            RecordingDiscord discord = harness.discord();
            assertThat(discord.nextSent().text()).isEqualTo("**Steve** joined the server");
            Waits.nothingIn(discord.sent);
        }
    }

    @Test
    void writesServerSwitchesInKorean() throws IOException {
        try (Harness harness = Harness.started(directory, "language: ko\n" + CHANNELS)) {
            harness.publish(new GameEvent.PlayerSwitchedServer(STEVE, "lobby", "survival"));

            assertThat(harness.discord().nextSent().text()).isEqualTo("**Steve**님이 **survival** 서버로 이동했습니다");
        }
    }

    @Test
    void escapesMarkdownInDeathMessagesAndTitles() throws IOException {
        try (Harness harness = Harness.started(directory, CHANNELS)) {
            harness.publish(new GameEvent.PlayerDied(STEVE, new RichText("{}", "Steve was slain by **Boss**")));
            harness.publish(new GameEvent.AdvancementEarned(STEVE, title("<@&1> *x*"), AdvancementFrame.TASK));

            assertThat(harness.discord().nextSent().text()).isEqualTo("Steve was slain by \\*\\*Boss\\*\\*");
            assertThat(harness.discord().nextSent().text())
                    .isEqualTo("**Steve** has made the advancement **\\<@&1\\> \\*x\\***");
        }
    }

    private static RichText title(String text) {
        return new RichText("{\"text\":\"" + text + "\"}", text);
    }
}
