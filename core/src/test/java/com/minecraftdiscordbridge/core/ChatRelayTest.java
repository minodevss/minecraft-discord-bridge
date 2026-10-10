package com.minecraftdiscordbridge.core;

import static com.minecraftdiscordbridge.core.Harness.ALEX;
import static com.minecraftdiscordbridge.core.Harness.CHANNELS;
import static com.minecraftdiscordbridge.core.Harness.CHAT;
import static com.minecraftdiscordbridge.core.Harness.EVENTS;
import static com.minecraftdiscordbridge.core.Harness.MEMBER;
import static com.minecraftdiscordbridge.core.Harness.STEVE;
import static org.assertj.core.api.Assertions.assertThat;

import com.minecraftdiscordbridge.core.discord.Author;
import com.minecraftdiscordbridge.core.discord.DiscordMessage;
import com.minecraftdiscordbridge.core.discord.OutboundMessage;
import com.minecraftdiscordbridge.core.platform.Delivery;
import com.minecraftdiscordbridge.core.platform.GameEvent;
import com.minecraftdiscordbridge.core.storage.Edition;
import com.minecraftdiscordbridge.core.storage.Link;
import com.minecraftdiscordbridge.core.storage.LinkStore;
import com.minecraftdiscordbridge.core.text.TextRenderer;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ChatRelayTest {
    @TempDir
    Path directory;

    @Test
    void sendsMinecraftChatAsThePlayerWithMarkdownEscaped() throws IOException {
        try (Harness harness = Harness.started(directory, CHANNELS)) {
            harness.publish(new GameEvent.PlayerChatted(STEVE, "**hi** _there_ @everyone"));

            OutboundMessage sent = harness.discord().nextSent();
            assertThat(sent.channelId()).isEqualTo(CHAT);
            assertThat(sent.author())
                    .isEqualTo(Author.player(STEVE.id().toString(), "Steve", "https://crafthead.net/helm/abc"));
            assertThat(sent.text()).isEqualTo("\\*\\*hi\\*\\* \\_there\\_ @everyone");
            assertThat(sent.pingUserIds()).isEmpty();
            assertThat(sent.pingRoleIds()).isEmpty();
        }
    }

    @Test
    void usesTheFallbackAvatarWhenTheSkinIsUnknown() throws IOException {
        try (Harness harness = Harness.started(directory, CHANNELS)) {
            harness.publish(new GameEvent.PlayerChatted(ALEX, "hello"));

            assertThat(harness.discord().nextSent().author().avatarUrl())
                    .isEqualTo("https://crafthead.net/helm/" + ALEX.id());
        }
    }

    @Test
    void dropsMinecraftChatWithABlockedWord() throws IOException {
        try (Harness harness = Harness.started(directory, CHANNELS + """
                chat:
                  blocked-words: ["(?i)badword"]
                """)) {
            harness.publish(new GameEvent.PlayerChatted(STEVE, "a BadWord here"));
            harness.publish(new GameEvent.PlayerChatted(STEVE, "fine"));

            assertThat(harness.discord().nextSent().text()).isEqualTo("fine");
        }
    }

    @Test
    void pingsOnlyLinkedPlayersAndAtMostThree() throws IOException {
        LinkStore links = LinkStore.open(directory);
        for (int i = 1; i <= 4; i++) {
            links.link(
                    new Link(UUID.randomUUID(), "10000000000000000" + i, "Friend_" + i, Edition.JAVA, Instant.EPOCH));
        }
        try (Harness harness = Harness.started(directory, CHANNELS + """
                chat:
                  ping-linked-players: true
                """)) {
            harness.publish(new GameEvent.PlayerChatted(
                    STEVE, "@friend_1 @Friend_2 @Friend_3 @Friend_4 @nobody mail@Friend_1"));

            OutboundMessage sent = harness.discord().nextSent();
            assertThat(sent.text())
                    .isEqualTo("<@100000000000000001> <@100000000000000002> <@100000000000000003> @Friend\\_4 @nobody"
                            + " mail@Friend\\_1");
            assertThat(sent.pingUserIds())
                    .containsExactlyInAnyOrder(100000000000000001L, 100000000000000002L, 100000000000000003L);
        }
    }

    @Test
    void leavesAtNamesAloneWhenPingsAreOff() throws IOException {
        LinkStore.open(directory)
                .link(new Link(UUID.randomUUID(), "100000000000000001", "Friend", Edition.JAVA, Instant.EPOCH));
        try (Harness harness = Harness.started(directory, CHANNELS)) {
            harness.publish(new GameEvent.PlayerChatted(STEVE, "@Friend"));

            OutboundMessage sent = harness.discord().nextSent();
            assertThat(sent.text()).isEqualTo("@Friend");
            assertThat(sent.pingUserIds()).isEmpty();
        }
    }

    @Test
    void showsDiscordChatToPlayersExceptThoseWhoHidIt() throws IOException {
        try (Harness harness = Harness.started(directory, CHANNELS)) {
            RecordingSource steve = harness.run(RecordingSource.player(STEVE.id(), "Steve"), "toggle");
            assertThat(steve.nextPlain()).startsWith("Messages from Discord are hidden");

            harness.discord()
                    .receive(new DiscordMessage(
                            CHAT,
                            1,
                            new DiscordMessage.Sender(MEMBER, "Alex", null, false, false),
                            "**hello** <@" + MEMBER + ">",
                            Map.of(MEMBER, "Alex"),
                            Map.of(),
                            Map.of(),
                            List.of(),
                            null,
                            List.of(),
                            false,
                            false,
                            false));

            Delivery delivery = Waits.take(harness.platform.deliveries);
            assertThat(plain(delivery)).isEqualTo("[Discord] Alex: hello @Alex");
            assertThat(delivery.excluded()).containsExactly(STEVE.id());
            assertThat(delivery.json()).contains("\"bold\":true");
        }
    }

    @Test
    void showsHiddenMessagesAgainAfterASecondToggle() throws IOException {
        try (Harness harness = Harness.started(directory, CHANNELS)) {
            RecordingSource steve = RecordingSource.player(STEVE.id(), "Steve");
            harness.run(steve, "toggle").nextPlain();
            assertThat(harness.run(steve, "toggle").nextPlain()).isEqualTo("Messages from Discord are shown again.");

            harness.discord().receive(Harness.message(CHAT, "hi"));

            assertThat(Waits.take(harness.platform.deliveries).excluded()).isEmpty();
        }
    }

    @Test
    void labelsAttachmentsStickersAndPollsAndLinksOnlyDiscordFiles() throws IOException {
        try (Harness harness = Harness.started(directory, CHANNELS)) {
            DiscordMessage message = withExtras(
                    "look",
                    List.of(
                            new DiscordMessage.Attachment(
                                    "cat.png",
                                    "https://cdn.discordapp.com/attachments/1/2/cat.png",
                                    "image/png",
                                    true,
                                    false),
                            new DiscordMessage.Attachment(
                                    "notes.txt", "https://evil.example/notes.txt", "text/plain", false, false)),
                    null,
                    List.of("wave"),
                    true);

            harness.discord().receive(message);

            Delivery delivery = Waits.take(harness.platform.deliveries);
            assertThat(plain(delivery))
                    .isEqualTo("[Discord] Alex: look [image: cat.png] [file: notes.txt] [sticker: wave] [poll]");
            assertThat(delivery.json())
                    .contains("https://cdn.discordapp.com/attachments/1/2/cat.png")
                    .doesNotContain("evil.example");
        }
    }

    @Test
    void showsTheRepliedMessageAboveTheReply() throws IOException {
        try (Harness harness = Harness.started(directory, CHANNELS)) {
            harness.discord()
                    .receive(withExtras(
                            "me too", List.of(), new DiscordMessage.Reply("Bob", "first\nline"), List.of(), false));

            assertThat(plain(Waits.take(harness.platform.deliveries)))
                    .isEqualTo("↳ Bob: first line\n[Discord] Alex: me too");
        }
    }

    @Test
    void colorsTheAuthorWithTheirRoleColor() throws IOException {
        try (Harness harness = Harness.started(directory, CHANNELS)) {
            harness.discord()
                    .receive(new DiscordMessage(
                            CHAT,
                            1,
                            new DiscordMessage.Sender(MEMBER, "Alex", 0x3498db, false, false),
                            "hi",
                            Map.of(),
                            Map.of(),
                            Map.of(),
                            List.of(),
                            null,
                            List.of(),
                            false,
                            false,
                            false));

            assertThat(Waits.take(harness.platform.deliveries).json()).containsIgnoringCase("#3498db");
        }
    }

    @Test
    void ignoresOtherChannelsBlockedWordsAndEmptyMessages() throws IOException {
        try (Harness harness = Harness.started(directory, CHANNELS + """
                chat:
                  blocked-words: ["secret"]
                """)) {
            harness.discord().receive(Harness.message(EVENTS, "wrong channel"));
            harness.discord().receive(Harness.message(CHAT, "a secret"));
            harness.discord().receive(Harness.message(CHAT, "   "));
            harness.discord().receive(Harness.message(CHAT, "kept"));

            assertThat(plain(Waits.take(harness.platform.deliveries))).isEqualTo("[Discord] Alex: kept");
            Waits.nothingIn(harness.platform.deliveries);
        }
    }

    @Test
    void relaysNothingWhenBothDirectionsAreOff() throws IOException {
        try (Harness harness = Harness.started(directory, CHANNELS + """
                chat:
                  minecraft-to-discord: false
                  discord-to-minecraft: false
                """)) {
            harness.publish(new GameEvent.PlayerChatted(STEVE, "hi"));
            harness.discord().receive(Harness.message(CHAT, "hi"));

            Waits.nothingIn(harness.discord().sent);
            Waits.nothingIn(harness.platform.deliveries);
        }
    }

    @Test
    void neverParsesDiscordTextAsMiniMessage() throws IOException {
        try (Harness harness = Harness.started(directory, CHANNELS)) {
            harness.discord().receive(Harness.message(CHAT, "<red>red</red> <click:run_command:/op me>x</click>"));

            Delivery delivery = Waits.take(harness.platform.deliveries);
            assertThat(plain(delivery)).isEqualTo("[Discord] Alex: <red>red</red> <click:run_command:/op me>x</click>");
            assertThat(delivery.json()).doesNotContain("click_event", "clickEvent", "\"color\":\"red\"");
        }
    }

    @Test
    void usesTheDefaultFormatWhileChatFormatIsInvalid() throws IOException {
        try (Harness harness = Harness.started(directory, CHANNELS + """
                chat:
                  format: "§a<author>: <message>"
                """)) {
            harness.discord().receive(Harness.message(CHAT, "hi"));

            assertThat(plain(Waits.take(harness.platform.deliveries))).isEqualTo("[Discord] Alex: hi");
        }
    }

    private static DiscordMessage withExtras(
            String content,
            List<DiscordMessage.Attachment> attachments,
            DiscordMessage.Reply reply,
            List<String> stickers,
            boolean poll) {
        return new DiscordMessage(
                CHAT,
                1,
                new DiscordMessage.Sender(MEMBER, "Alex", null, false, false),
                content,
                Map.of(),
                Map.of(),
                Map.of(),
                attachments,
                reply,
                stickers,
                false,
                poll,
                false);
    }

    private static String plain(Delivery delivery) {
        return TextRenderer.plain(TextRenderer.fromJson(delivery.json()));
    }
}
