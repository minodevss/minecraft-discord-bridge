package com.minecraftdiscordbridge.core.discord;

import static com.minecraftdiscordbridge.core.discord.FakeDiscord.APPLICATION_ID;
import static com.minecraftdiscordbridge.core.discord.FakeDiscord.BOT_ID;
import static com.minecraftdiscordbridge.core.discord.FakeDiscord.CHANNEL_ID;
import static com.minecraftdiscordbridge.core.discord.FakeDiscord.FOREIGN_WEBHOOK_ID;
import static com.minecraftdiscordbridge.core.discord.FakeDiscord.FRIEND_ID;
import static com.minecraftdiscordbridge.core.discord.FakeDiscord.GUILD_ID;
import static com.minecraftdiscordbridge.core.discord.FakeDiscord.MESSAGE_CONTENT_INTENT;
import static com.minecraftdiscordbridge.core.discord.FakeDiscord.OTHER_BOT_ID;
import static com.minecraftdiscordbridge.core.discord.FakeDiscord.OTHER_GUILD_ID;
import static com.minecraftdiscordbridge.core.discord.FakeDiscord.PLAYER_ID;
import static com.minecraftdiscordbridge.core.discord.FakeDiscord.ROLE_ID;
import static com.minecraftdiscordbridge.core.discord.TestSupport.await;
import static com.minecraftdiscordbridge.core.discord.TestSupport.take;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.minecraftdiscordbridge.core.RecordingLogs;
import com.minecraftdiscordbridge.core.discord.DiscordStatus.State;
import com.minecraftdiscordbridge.core.discord.TestSupport.MemoryCommandHashes;
import com.minecraftdiscordbridge.core.discord.TestSupport.MemoryWebhooks;
import com.minecraftdiscordbridge.core.discord.TestSupport.RecordingListener;
import com.minecraftdiscordbridge.core.loop.CoreLoop;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;
import mockwebserver3.RecordedRequest;
import net.dv8tion.jda.api.utils.data.DataArray;
import net.dv8tion.jda.api.utils.data.DataObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

class JdaDiscordTest {
    private static final Author STEVE = Author.player("uuid-steve", "Steve", "https://mc-heads.net/avatar/Steve");
    private static final Author SERVER = Author.server("Survival", "https://example.com/server.png");
    private static final int GUILD_MEMBERS_INTENT = 1 << 1;
    private static final Duration SHUTDOWN_LIMIT = JdaDiscord.SHUTDOWN_BUDGET.plusMillis(250);
    private static final CommandSpec LIST = new CommandSpec(
            "list",
            "Show who is online",
            new CommandSpec.Localized("목록", "접속 중인 플레이어"),
            List.of(new CommandSpec.Option(CommandSpec.OptionType.INTEGER, "page", "Page", null, false, false)),
            false,
            false,
            false);
    private static final CommandSpec WHITELIST = new CommandSpec(
            "whitelist",
            "Add a player to the whitelist",
            null,
            List.of(new CommandSpec.Option(
                    CommandSpec.OptionType.STRING,
                    "player",
                    "Player name",
                    new CommandSpec.Localized("플레이어", "플레이어 이름"),
                    true,
                    true)),
            true,
            true,
            false);

    private FakeDiscord discord;
    private CoreLoop loop;
    private RecordingListener listener;
    private MemoryWebhooks webhooks;
    private MemoryCommandHashes commandHashes;
    private JdaDiscord bridge;

    @BeforeEach
    void startFakeDiscord() throws Exception {
        discord = new FakeDiscord();
        loop = new CoreLoop();
        listener = new RecordingListener();
        webhooks = new MemoryWebhooks();
        commandHashes = new MemoryCommandHashes();
    }

    @AfterEach
    void stopEverything() throws Exception {
        if (bridge != null) {
            bridge.shutdown(List.of());
        }
        loop.close();
        discord.close();
    }

    @Test
    void becomesReadyAfterTheGatewaySaysReady() {
        bridge = start(TestSupport.settings(List.of()));

        awaitState(State.READY);
        assertThat(bridge.status().reason()).isNull();
        assertThat(take(listener.statuses).state()).isEqualTo(State.READY);
    }

    @Test
    void stopsWithAReasonInsteadOfThrowingWhenTheTokenIsInvalid() {
        discord.tokenValid = false;

        assertThatCode(() -> bridge = start(TestSupport.settings(List.of()))).doesNotThrowAnyException();

        awaitState(State.STOPPED);
        assertThat(bridge.status().reason()).contains("rejected the bot token");
        assertThat(discord.timeline).containsExactly("GET applications/@me");
    }

    @Test
    void turnsOnMessageContentBeforeLoggingInAndKeepsTheOtherFlags() {
        long otherFlags = (1L << 12) | (1L << 23) | ApplicationInfo.GATEWAY_GUILD_MEMBERS_LIMITED;
        discord.applicationFlags = otherFlags;

        bridge = start(TestSupport.settings(List.of()));

        awaitState(State.READY);
        RecordedRequest patch = discord.requests("PATCH", "applications/@me").get(0);
        assertThat(FakeDiscord.body(patch).getLong("flags"))
                .isEqualTo(otherFlags | ApplicationInfo.GATEWAY_MESSAGE_CONTENT_LIMITED);
        assertThat(discord.timeline.indexOf("PATCH applications/@me"))
                .isLessThan(discord.timeline.indexOf("GET users/@me"))
                .isLessThan(discord.timeline.indexOf("GATEWAY connect"));
        assertThat(discord.identifies.get(0).getInt("intents") & MESSAGE_CONTENT_INTENT)
                .isNotZero();
    }

    @Test
    void degradesWithoutMessageContentWhenNotAllowedToTurnItOn() {
        discord.applicationFlags = 0;

        bridge = start(withAutoEnable(false));

        awaitState(State.DEGRADED);
        assertThat(bridge.status().reason())
                .contains("Message Content")
                .contains("https://discord.com/developers/applications/" + APPLICATION_ID + "/bot");
        assertThat(discord.requests("PATCH", "applications/@me")).isEmpty();
        assertThat(discord.identifies.get(0).getInt("intents") & MESSAGE_CONTENT_INTENT)
                .isZero();
    }

    @Test
    void degradesWhenDiscordRefusesToTurnOnMessageContent() {
        discord.applicationFlags = 0;
        discord.patchAllowed = false;

        bridge = start(TestSupport.settings(List.of()));

        awaitState(State.DEGRADED);
        assertThat(discord.requests("PATCH", "applications/@me")).hasSize(1);
        assertThat(discord.identifies.get(0).getInt("intents") & MESSAGE_CONTENT_INTENT)
                .isZero();
    }

    @Test
    void reconnectsOnceWithoutMessageContentWhenDiscordRefusesTheIntent() {
        discord.refuseMessageContent = true;

        bridge = start(TestSupport.settings(List.of()));

        awaitState(State.DEGRADED);
        assertThat(discord.identifies).hasSize(2);
        assertThat(discord.identifies.get(0).getInt("intents") & MESSAGE_CONTENT_INTENT)
                .isNotZero();
        assertThat(discord.identifies.get(1).getInt("intents") & MESSAGE_CONTENT_INTENT)
                .isZero();
        assertThat(bridge.status().reason()).contains("refused the Message Content intent");
    }

    @Test
    void reportsHowToInviteTheBotWhenItIsNotInTheServer() {
        discord.guildMember = false;

        bridge = start(TestSupport.settings(List.of()));

        awaitState(State.DEGRADED);
        assertThat(bridge.status().reason())
                .contains("not in Discord server " + GUILD_ID)
                .contains("client_id=" + APPLICATION_ID + "&scope=bot+applications.commands");
    }

    @Test
    void relaysMessagesFromTheBoundChannelWithResolvedDetails() {
        bridge = startReady();
        DataObject friend = DataObject.fromJson(FakeDiscord.user(FRIEND_ID, "Friend", false))
                .put("member", FakeDiscord.member("Bestie"));
        DataObject message = discord.message(
                        PLAYER_ID, "hi <@" + FRIEND_ID + "> <@&" + ROLE_ID + "> <#" + CHANNEL_ID + ">")
                .put("member", FakeDiscord.member("Alex"))
                .put("mentions", DataArray.empty().add(friend))
                .put("mention_roles", DataArray.empty().add(Long.toString(ROLE_ID)))
                .put("attachments", DataArray.empty().add(attachment()))
                .put("sticker_items", DataArray.empty().add(sticker()))
                .put("message_reference", reference(0))
                .put("referenced_message", discord.message(FRIEND_ID, "x".repeat(100)));

        discord.sendMessage(message);

        DiscordMessage received = take(listener.messages);
        assertThat(received.channelId()).isEqualTo(CHANNEL_ID);
        assertThat(received.author()).isEqualTo(new DiscordMessage.Sender(PLAYER_ID, "Alex", 0x3498db, false, false));
        assertThat(received.content()).startsWith("hi <@" + FRIEND_ID + ">");
        assertThat(received.mentionedUsers()).containsEntry(FRIEND_ID, "Bestie");
        assertThat(received.mentionedRoles())
                .containsEntry(ROLE_ID, new DiscordMessage.MentionedRole("Builders", 0x3498db));
        assertThat(received.mentionedChannels()).containsEntry(CHANNEL_ID, "minecraft");
        assertThat(received.attachments())
                .containsExactly(new DiscordMessage.Attachment(
                        "cat.png", "https://cdn.example.com/cat.png", "image/png", true, false));
        assertThat(received.stickers()).containsExactly("wave");
        assertThat(received.reply()).isNotNull();
        assertThat(received.reply().authorName()).isEqualTo("Author" + FRIEND_ID);
        assertThat(received.reply().excerpt()).isEqualTo("x".repeat(80) + "…");
        assertThat(received.forwarded()).isFalse();
        assertThat(received.poll()).isFalse();
        assertThat(received.voiceMessage()).isFalse();
    }

    @Test
    void marksForwardedAndVoiceMessages() {
        bridge = startReady();
        DataObject snapshot = DataObject.empty()
                .put("message", discord.message(FRIEND_ID, "original").remove("author"));

        discord.sendMessage(discord.message(PLAYER_ID, "")
                .put("message_reference", reference(1))
                .put("message_snapshots", DataArray.empty().add(snapshot)));
        discord.sendMessage(discord.message(PLAYER_ID, "").put("flags", 1 << 13));

        DiscordMessage forwarded = take(listener.messages);
        DiscordMessage voice = take(listener.messages);
        assertThat(forwarded.forwarded()).isTrue();
        assertThat(forwarded.reply()).isNull();
        assertThat(voice.voiceMessage()).isTrue();
    }

    @Test
    void ignoresBotsWebhooksAndItsOwnMessages() {
        bridge = startReady();

        discord.sendMessage(discord.message(OTHER_BOT_ID, "beep")
                .put("author", DataObject.fromJson(FakeDiscord.user(OTHER_BOT_ID, "Other", true))));
        discord.sendMessage(
                discord.message(FOREIGN_WEBHOOK_ID, "hook").put("webhook_id", Long.toString(FOREIGN_WEBHOOK_ID)));
        discord.sendMessage(discord.message(FOREIGN_WEBHOOK_ID + 1, "ours")
                .put("webhook_id", Long.toString(FOREIGN_WEBHOOK_ID + 1))
                .put("application_id", Long.toString(APPLICATION_ID)));
        discord.sendMessage(discord.message(BOT_ID, "self"));
        discord.sendMessage(discord.message(PLAYER_ID, "real").put("channel_id", "999"));
        discord.sendMessage(discord.message(PLAYER_ID, "real"));

        assertThat(take(listener.messages).content()).isEqualTo("real");
        assertThat(listener.messages).isEmpty();
    }

    @Test
    void sendsMessagesQueuedBeforeDiscordWasReady() {
        bridge = start(TestSupport.settings(List.of()));
        bridge.send(OutboundMessage.of(CHANNEL_ID, SERVER, "Server started"));

        awaitState(State.READY);
        await("queued message", () -> executions().size() == 1);
        assertThat(FakeDiscord.body(executions().get(0)).getString("content")).isEqualTo("Server started");
        assertThat(discord.requests("POST", "channels/" + CHANNEL_ID + "/webhooks"))
                .hasSize(1);
    }

    @Test
    void sendsPlayerMessagesThroughOneWebhookItCreates() {
        bridge = startReady();

        bridge.send(new OutboundMessage(CHANNEL_ID, STEVE, "hello <@" + FRIEND_ID + ">", Set.of(FRIEND_ID)));
        await("first message", () -> executions().size() == 1);
        bridge.send(OutboundMessage.of(CHANNEL_ID, STEVE, "again"));
        await("second message", () -> executions().size() == 2);

        List<RecordedRequest> creations = discord.requests("POST", "channels/" + CHANNEL_ID + "/webhooks");
        assertThat(creations).hasSize(1);
        assertThat(FakeDiscord.body(creations.get(0)).getString("name")).isEqualTo("Minecraft Discord Bridge");
        DataObject first = FakeDiscord.body(executions().get(0));
        assertThat(first.getString("content")).isEqualTo("hello <@" + FRIEND_ID + ">");
        assertThat(first.getString("username")).isEqualTo("Steve");
        assertThat(first.getString("avatar_url")).isEqualTo("https://mc-heads.net/avatar/Steve");
        DataObject mentions = first.getObject("allowed_mentions");
        assertThat(mentions.getArray("parse").length()).isZero();
        assertThat(mentions.getArray("users").getString(0)).isEqualTo(Long.toString(FRIEND_ID));
        assertThat(mentions.getBoolean("replied_user")).isFalse();
        assertThat(webhooks.stored).containsKey(CHANNEL_ID);
    }

    @Test
    void recreatesTheWebhookOnceWhenDiscordNoLongerKnowsIt() {
        webhooks.stored.put(CHANNEL_ID, new WebhookCredentials(777, "stale"));
        discord.webhookGoneResponses.set(1);
        bridge = startReady();

        bridge.send(OutboundMessage.of(CHANNEL_ID, STEVE, "hello"));

        await("resent message", () -> executions().size() == 2);
        assertThat(discord.requestsStartingWith("POST", "webhooks/777/")).hasSize(1);
        assertThat(discord.requests("POST", "channels/" + CHANNEL_ID + "/webhooks"))
                .hasSize(1);
        assertThat(webhooks.stored.get(CHANNEL_ID).id()).isNotEqualTo(777);
    }

    @Test
    void postsAsTheBotWhenItCannotManageWebhooks() {
        discord.everyonePermissions = FakeDiscord.NO_WEBHOOKS;
        bridge = startReady();

        bridge.send(OutboundMessage.of(CHANNEL_ID, STEVE, "hi"));

        await("bot message", () -> botMessages().size() == 1);
        DataObject body = FakeDiscord.body(botMessages().get(0));
        assertThat(body.getString("content")).isEqualTo("**Steve**: hi");
        assertThat(body.getObject("allowed_mentions").getArray("parse").length())
                .isZero();
        assertThat(discord.requestsStartingWith("POST", "webhooks/")).isEmpty();
    }

    @Test
    void countsRateLimitedRequestsPerChannelAndStillDelivers() {
        discord.rateLimitedResponses.set(1);
        bridge = startReady();

        bridge.send(OutboundMessage.of(CHANNEL_ID, STEVE, "hello"));

        await("retried message", () -> executions().size() == 2);
        await("429 count", () -> bridge.status().channels().get(CHANNEL_ID).rateLimited() == 1);
    }

    @Test
    void registersSlashCommandsOnlyWhenTheyChange() throws Exception {
        bridge = start(TestSupport.settings(List.of(LIST, WHITELIST)));
        awaitState(State.READY);
        await("registration", () -> registrations().size() == 1);

        DataArray commands = DataArray.fromJson(registrations().get(0).getBody().utf8());
        DataObject list = commands.getObject(0);
        DataObject whitelist = commands.getObject(1);
        assertThat(list.getObject("name_localizations").getString("ko")).isEqualTo("목록");
        assertThat(list.isNull("default_member_permissions")).isTrue();
        assertThat(list.getArray("contexts").getInt(0)).isZero();
        assertThat(whitelist.getString("default_member_permissions")).isEqualTo("0");
        assertThat(whitelist.getArray("contexts").length()).isEqualTo(1);
        assertThat(whitelist.getArray("options").getObject(0).getBoolean("autocomplete"))
                .isTrue();
        await("hash saved", () -> commandHashes.stored.containsKey(GUILD_ID));

        bridge.shutdown(List.of());
        bridge = start(TestSupport.settings(List.of(LIST, WHITELIST)));
        await(
                "second ready",
                () -> discord.identifies.size() == 2 && bridge.status().state() == State.READY);
        Thread.sleep(200);

        assertThat(registrations()).hasSize(1);
    }

    @Test
    void refusesCommandsFromServersItIsNotSetUpFor() {
        bridge = start(TestSupport.settings(List.of(LIST)));
        awaitState(State.READY);

        discord.dispatch("INTERACTION_CREATE", discord.command(OTHER_GUILD_ID, "list", 2, DataArray.empty()));

        await("refusal", () -> callbacks().size() == 1);
        DataObject callback = FakeDiscord.body(callbacks().get(0));
        assertThat(callback.getInt("type")).isEqualTo(4);
        assertThat(callback.getObject("data").getString("content"))
                .isEqualTo("This bot is not set up for this server.");
        assertThat(callback.getObject("data").getInt("flags") & 64).isNotZero();
        assertThat(listener.commands).isEmpty();
    }

    @Test
    void handsCommandsToTheCoreWithAReplyHandle() {
        bridge = start(TestSupport.settings(List.of(LIST)));
        awaitState(State.READY);
        DataArray options = DataArray.empty()
                .add(DataObject.empty().put("name", "page").put("type", 4).put("value", 2));

        discord.dispatch("INTERACTION_CREATE", discord.command(GUILD_ID, "list", 2, options));

        InteractionRequest request = take(listener.commands);
        assertThat(request.command()).isEqualTo("list");
        assertThat(request.options()).containsEntry("page", "2");
        assertThat(request.userId()).isEqualTo(PLAYER_ID);
        assertThat(request.userDisplayName()).isEqualTo("Alex");
        assertThat(request.locale()).isEqualTo("ko");
        assertThat(request.guildId()).isEqualTo(GUILD_ID);
        assertThat(request.channelId()).isEqualTo(CHANNEL_ID);
        assertThat(callbacks()).isEmpty();

        take(listener.replies).replyEphemeral("3 players online");

        await("reply", () -> callbacks().size() == 1);
        DataObject callback = FakeDiscord.body(callbacks().get(0));
        assertThat(callback.getInt("type")).isEqualTo(4);
        assertThat(callback.getObject("data").getString("content")).isEqualTo("3 players online");
        assertThat(callback.getObject("data").getInt("flags") & 64).isNotZero();
    }

    @Test
    void defersSlowCommandsBeforeTheCoreAnswers() {
        bridge = start(TestSupport.settings(List.of(WHITELIST)));
        awaitState(State.READY);
        DataArray options = DataArray.empty()
                .add(DataObject.empty().put("name", "player").put("type", 3).put("value", "Steve"));

        discord.dispatch("INTERACTION_CREATE", discord.command(GUILD_ID, "whitelist", 2, options));

        await("deferral", () -> callbacks().size() == 1);
        assertThat(FakeDiscord.body(callbacks().get(0)).getInt("type")).isEqualTo(5);

        take(listener.replies).reply("Added Steve");

        await("edited reply", () -> originalEdits().size() == 1);
        DataObject edit = FakeDiscord.body(originalEdits().get(0));
        assertThat(edit.getString("content")).isEqualTo("Added Steve");
        assertThat(edit.getObject("allowed_mentions").getArray("parse").length())
                .isZero();
    }

    @Test
    void answersAutocompleteWithAtMostTwentyFiveChoices() {
        bridge = start(TestSupport.settings(List.of(WHITELIST)));
        awaitState(State.READY);
        DataArray options = DataArray.empty()
                .add(DataObject.empty()
                        .put("name", "player")
                        .put("type", 3)
                        .put("value", "St")
                        .put("focused", true));

        discord.dispatch("INTERACTION_CREATE", discord.command(GUILD_ID, "whitelist", 4, options));

        AutocompleteRequest request = take(listener.autocompletes);
        assertThat(request.option()).isEqualTo("player");
        assertThat(request.typed()).isEqualTo("St");
        List<AutocompleteReply.Choice> choices = IntStream.range(0, 30)
                .mapToObj(i -> new AutocompleteReply.Choice("Steve" + i, "Steve" + i))
                .toList();
        take(listener.autocompleteReplies).respond(choices);

        await("choices", () -> callbacks().size() == 1);
        DataObject callback = FakeDiscord.body(callbacks().get(0));
        assertThat(callback.getInt("type")).isEqualTo(8);
        assertThat(callback.getObject("data").getArray("choices").length()).isEqualTo(25);
    }

    @Test
    void setsEveryMessageMentionPolicyExplicitlyAndNeverAllowsEveryoneOrRoles() {
        bridge = start(TestSupport.settings(List.of(LIST, WHITELIST)));
        awaitState(State.READY);
        bridge.send(new OutboundMessage(CHANNEL_ID, STEVE, "@everyone <@&" + ROLE_ID + ">", Set.of(FRIEND_ID)));
        discord.dispatch("INTERACTION_CREATE", discord.command(GUILD_ID, "list", 2, DataArray.empty()));
        take(listener.replies).reply("@here");
        discord.dispatch("INTERACTION_CREATE", discord.command(GUILD_ID, "whitelist", 2, DataArray.empty()));
        take(listener.replies).reply("@everyone");
        await(
                "all messages",
                () -> executions().size() == 1
                        && callbacks().size() == 2
                        && originalEdits().size() == 1);
        bridge.shutdown(List.of(OutboundMessage.of(CHANNEL_ID, SERVER, "Server stopped @everyone")));
        bridge = null;

        List<DataObject> messages = new ArrayList<>();
        executions().forEach(request -> messages.add(FakeDiscord.body(request)));
        originalEdits().forEach(request -> messages.add(FakeDiscord.body(request)));
        callbacks().stream()
                .map(FakeDiscord::body)
                .filter(callback -> callback.getInt("type") == 4)
                .forEach(callback -> messages.add(callback.getObject("data")));
        assertThat(messages).hasSize(4);
        for (DataObject message : messages) {
            DataObject mentions = message.getObject("allowed_mentions");
            assertThat(mentions.getArray("parse").length())
                    .as(message.toString())
                    .isZero();
            assertThat(mentions.hasKey("roles")).as(message.toString()).isFalse();
        }
    }

    @Test
    void sendsTheFinalMessageBeforeDisconnecting() {
        bridge = startReady();
        bridge.send(OutboundMessage.of(CHANNEL_ID, STEVE, "bye"));
        await("first message", () -> executions().size() == 1);

        bridge.shutdown(List.of(OutboundMessage.of(CHANNEL_ID, SERVER, "Server stopped")));

        assertThat(executions()).hasSize(2);
        DataObject last = FakeDiscord.body(executions().get(1));
        assertThat(last.getString("content")).isEqualTo("Server stopped");
        assertThat(last.getString("username")).isEqualTo("Survival");
        assertThat(bridge.status().state()).isEqualTo(State.STOPPED);
    }

    @Test
    void finishesShutdownWithinThreeSecondsWhenDiscordStopsAnswering() {
        bridge = startReady();
        bridge.send(OutboundMessage.of(CHANNEL_ID, STEVE, "warm up"));
        await("webhook ready", () -> executions().size() == 1);
        discord.stallMessages = true;
        bridge.send(OutboundMessage.of(CHANNEL_ID, STEVE, "stuck"));
        await("stuck request", () -> executions().size() == 2);
        bridge.send(OutboundMessage.of(CHANNEL_ID, STEVE, "queued"));

        long started = System.nanoTime();
        bridge.shutdown(List.of(OutboundMessage.of(CHANNEL_ID, SERVER, "Server stopped")));

        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(SHUTDOWN_LIMIT);
        assertThat(bridge.status().state()).isEqualTo(State.STOPPED);
    }

    @Test
    void finishesShutdownWithinThreeSecondsWhileLoginHangs() {
        discord.stallLogin = true;
        bridge = start(TestSupport.settings(List.of()));
        await("login attempt", () -> discord.timeline.contains("GET users/@me"));

        long started = System.nanoTime();
        bridge.shutdown(List.of(OutboundMessage.of(CHANNEL_ID, SERVER, "Server stopped")));

        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(SHUTDOWN_LIMIT);
        assertThat(bridge.status().state()).isEqualTo(State.STOPPED);
        assertThat(discord.timeline).doesNotContain("GATEWAY connect");
    }

    @Test
    void updatesPresenceOnlyWhenTheTextChanges() throws Exception {
        bridge = startReady();

        bridge.updatePresence("3 players online");
        await("presence", () -> discord.presences.size() == 1);
        bridge.updatePresence("3 players online");
        Thread.sleep(200);

        assertThat(discord.presences).hasSize(1);
        DataObject activity = discord.presences.get(0).getArray("activities").getObject(0);
        assertThat(activity.getString("state")).isEqualTo("3 players online");
    }

    @Test
    void updatesTheChannelTopicOnlyWhenItChanges() throws Exception {
        bridge = startReady();

        bridge.updateTopic(CHANNEL_ID, "3/20 players online");
        await("topic", () -> topicEdits().size() == 1);
        bridge.updateTopic(CHANNEL_ID, "3/20 players online");
        bridge.updateTopic(CHANNEL_ID, "4/20 players online");
        Thread.sleep(200);

        assertThat(topicEdits()).hasSize(1);
        assertThat(FakeDiscord.body(topicEdits().get(0)).getString("topic")).isEqualTo("3/20 players online");
    }

    @Test
    void warnsOnceWhileTheBotCannotChangeTheTopic() {
        discord.everyonePermissions = FakeDiscord.NO_WEBHOOKS;
        bridge = startReady();
        AtomicLong now = new AtomicLong();
        Appearance appearance = new Appearance(bridge::currentJda, now::get, (delay, task) -> {});

        try (RecordingLogs.Capture logs = RecordingLogs.capture()) {
            for (int players = 1; players <= 3; players++) {
                appearance.topic(CHANNEL_ID, players + " players online");
                now.addAndGet(Appearance.TOPIC_INTERVAL.toNanos());
            }

            assertThat(logs.lines(Level.WARN))
                    .filteredOn(line -> line.contains("Manage Channel"))
                    .hasSize(1);
        }
        assertThat(topicEdits()).isEmpty();
    }

    @Test
    void knowsTheOwnerAndAcceptedAdminsOrDevelopersOfTheTeam() {
        bridge = startReady();

        assertThat(bridge.isApplicationOwner(PLAYER_ID)).isTrue();
        assertThat(bridge.isApplicationOwner(FRIEND_ID)).isTrue();
        assertThat(bridge.isApplicationOwner(OTHER_BOT_ID)).isFalse();
        assertThat(bridge.isApplicationOwner(12345)).isFalse();
    }

    @Test
    void registersCommandsInEveryServerItIsInUntilAChannelIsChosen() {
        bridge = start(unbound(Set.of(), List.of(LIST)));
        awaitState(State.READY);
        await("registration", () -> registrations().size() == 1);

        discord.dispatch("INTERACTION_CREATE", discord.command(GUILD_ID, "list", 2, DataArray.empty()));

        assertThat(take(listener.commands).guildId()).isEqualTo(GUILD_ID);
    }

    @Test
    void findsTheServerThroughTheConfiguredChannel() {
        bridge = start(unbound(Set.of(CHANNEL_ID), List.of(LIST)));
        awaitState(State.READY);
        await("registration", () -> registrations().size() == 1);

        discord.dispatch("INTERACTION_CREATE", discord.command(OTHER_GUILD_ID, "list", 2, DataArray.empty()));
        discord.sendMessage(discord.message(PLAYER_ID, "hello"));

        assertThat(take(listener.messages).content()).isEqualTo("hello");
        await("refusal", () -> callbacks().size() == 1);
        assertThat(listener.commands).isEmpty();
    }

    @Test
    void reportsWhenNoConfiguredChannelIsVisible() {
        bridge = start(unbound(Set.of(CHANNEL_ID + 1), List.of(LIST)));

        awaitState(State.DEGRADED);
        assertThat(bridge.status().reason()).contains("cannot see any of the configured channels");
        assertThat(registrations()).isEmpty();
    }

    @Test
    void passesTheSubcommandAndWhetherTheMemberCanManageTheServer() {
        CommandSpec whitelist = new CommandSpec(
                "whitelist",
                "Change the whitelist",
                null,
                List.of(),
                List.of(new CommandSpec.Subcommand(
                        "add",
                        "Add a player",
                        null,
                        List.of(new CommandSpec.Option(
                                CommandSpec.OptionType.STRING, "player", "Player", null, true, false)))),
                true,
                false,
                false);
        bridge = start(TestSupport.settings(List.of(whitelist)));
        awaitState(State.READY);
        DataArray options = DataArray.empty()
                .add(DataObject.empty()
                        .put("name", "add")
                        .put("type", 1)
                        .put(
                                "options",
                                DataArray.empty()
                                        .add(DataObject.empty()
                                                .put("name", "player")
                                                .put("type", 3)
                                                .put("value", "Steve"))));

        discord.dispatch("INTERACTION_CREATE", discord.command(GUILD_ID, "whitelist", 2, options));

        InteractionRequest request = take(listener.commands);
        assertThat(request.subcommand()).isEqualTo("add");
        assertThat(request.options()).containsEntry("player", "Steve");
        assertThat(request.canManageServer()).isTrue();
    }

    @Test
    void allowsMentionsOfExactlyTheRequestedRoles() {
        bridge = startReady();

        bridge.send(new OutboundMessage(
                CHANNEL_ID, SERVER, "<@&" + ROLE_ID + "> Server started", Set.of(), Set.of(ROLE_ID)));

        await("message", () -> executions().size() == 1);
        DataObject mentions = FakeDiscord.body(executions().get(0)).getObject("allowed_mentions");
        assertThat(mentions.getArray("parse").length()).isZero();
        assertThat(mentions.getArray("roles").length()).isEqualTo(1);
        assertThat(mentions.getArray("roles").getString(0)).isEqualTo(Long.toString(ROLE_ID));
    }

    @Test
    void postsServerMessagesUnderTheBotsNameAndAvatarWhenNoneIsGiven() {
        bridge = startReady();

        bridge.send(OutboundMessage.of(CHANNEL_ID, Author.server(), "Server started"));

        await("message", () -> executions().size() == 1);
        DataObject body = FakeDiscord.body(executions().get(0));
        assertThat(body.getString("username")).isEqualTo("Bridge");
        assertThat(body.getString("avatar_url")).startsWith("https://cdn.discordapp.com/embed/avatars/");
    }

    private static DiscordSettings unbound(Set<Long> channels, List<CommandSpec> commands) {
        DiscordSettings base = TestSupport.settings(commands);
        return new DiscordSettings(
                base.token(),
                DiscordSettings.NO_GUILD,
                channels,
                base.serverAuthor(),
                base.autoEnableMessageContent(),
                base.requestMembersIntent(),
                base.relayBotMessages(),
                base.relayWebhookMessages(),
                base.commands());
    }

    @Test
    void looksUpMemberRolesOverRestWithoutTheMembersIntent() throws Exception {
        bridge = startReady();

        MemberLookup found = bridge.member(PLAYER_ID).get(5, TimeUnit.SECONDS);

        assertThat(found).isEqualTo(new MemberLookup.Member(PLAYER_ID, Set.of(ROLE_ID)));
        assertThat(discord.requests("GET", "guilds/" + GUILD_ID + "/members/" + PLAYER_ID))
                .hasSize(1);
        assertThat(discord.identifies.get(0).getInt("intents") & GUILD_MEMBERS_INTENT)
                .isZero();
    }

    @Test
    void reportsUsersWhoAreNotInTheServer() throws Exception {
        bridge = startReady();

        assertThat(bridge.member(FRIEND_ID).get(5, TimeUnit.SECONDS)).isEqualTo(new MemberLookup.NotMember(FRIEND_ID));
    }

    @Test
    void reportsMemberLookupsAsUnavailableBeforeConnecting() throws Exception {
        bridge = new JdaDiscord(
                TestSupport.settings(List.of()), discord.endpoints(), loop, listener, webhooks, commandHashes);

        assertThat(bridge.member(PLAYER_ID).get(5, TimeUnit.SECONDS)).isInstanceOf(MemberLookup.Unavailable.class);
        assertThat(bridge.roles()).isEmpty();
    }

    @Test
    void describesRolesWithModeratorPermissions() {
        bridge = startReady();

        Map<Long, RoleInfo> roles = bridge.roles();

        assertThat(roles.get(GUILD_ID).dangerousPermissions()).contains("Administrator");
        assertThat(roles.get(ROLE_ID)).isEqualTo(new RoleInfo(ROLE_ID, "Builders", Set.of(), false));
    }

    @Test
    void givesAndTakesRolesWithAnAuditLogReason() throws Exception {
        discord.botManagesRoles = true;
        bridge = startReady();
        String route = "guilds/" + GUILD_ID + "/members/" + PLAYER_ID + "/roles/" + ROLE_ID;

        assertThat(bridge.roles().get(ROLE_ID).assignable()).isTrue();
        bridge.addRole(PLAYER_ID, ROLE_ID).get(5, TimeUnit.SECONDS);
        bridge.removeRole(PLAYER_ID, ROLE_ID).get(5, TimeUnit.SECONDS);

        assertThat(discord.requests("PUT", route))
                .singleElement()
                .satisfies(request -> assertThat(request.getHeaders().get("X-Audit-Log-Reason"))
                        .isNotBlank());
        assertThat(discord.requests("DELETE", route)).hasSize(1);
    }

    @Test
    void forwardsMemberRoleUpdatesAndDepartures() {
        bridge = startReady();

        discord.dispatch(
                "GUILD_MEMBER_UPDATE",
                FakeDiscord.member("Alex")
                        .put("guild_id", Long.toString(GUILD_ID))
                        .put("user", DataObject.fromJson(FakeDiscord.user(PLAYER_ID, "Alex", false))));
        discord.dispatch(
                "GUILD_MEMBER_REMOVE",
                DataObject.empty()
                        .put("guild_id", Long.toString(GUILD_ID))
                        .put("user", DataObject.fromJson(FakeDiscord.user(FRIEND_ID, "Friend", false))));

        assertThat(take(listener.memberUpdates)).isEqualTo(new MemberLookup.Member(PLAYER_ID, Set.of(ROLE_ID)));
        assertThat(take(listener.membersLeft)).isEqualTo(FRIEND_ID);
    }

    private JdaDiscord startReady() {
        JdaDiscord started = start(TestSupport.settings(List.of()));
        await("ready", () -> started.status().state() == State.READY);
        return started;
    }

    private JdaDiscord start(DiscordSettings settings) {
        JdaDiscord started = new JdaDiscord(settings, discord.endpoints(), loop, listener, webhooks, commandHashes);
        started.start();
        return started;
    }

    private void awaitState(State state) {
        await(state.name(), () -> bridge.status().state() == state);
    }

    private static DiscordSettings withAutoEnable(boolean autoEnable) {
        DiscordSettings base = TestSupport.settings(List.of());
        return new DiscordSettings(
                base.token(),
                base.guildId(),
                base.channelIds(),
                base.serverAuthor(),
                autoEnable,
                base.requestMembersIntent(),
                base.relayBotMessages(),
                base.relayWebhookMessages(),
                base.commands());
    }

    private List<RecordedRequest> executions() {
        return discord.requestsStartingWith("POST", "webhooks/");
    }

    private List<RecordedRequest> botMessages() {
        return discord.requests("POST", "channels/" + CHANNEL_ID + "/messages");
    }

    private List<RecordedRequest> registrations() {
        return discord.requestsStartingWith("PUT", "applications/" + APPLICATION_ID + "/guilds/" + GUILD_ID);
    }

    private List<RecordedRequest> topicEdits() {
        return discord.requests("PATCH", "channels/" + CHANNEL_ID);
    }

    private List<RecordedRequest> callbacks() {
        return discord.requestsStartingWith("POST", "interactions/");
    }

    private List<RecordedRequest> originalEdits() {
        return discord.requestsStartingWith(
                "PATCH", "webhooks/" + APPLICATION_ID + "/interaction-token/messages/@original");
    }

    private static DataObject attachment() {
        return DataObject.empty()
                .put("id", "610000000000000006")
                .put("filename", "cat.png")
                .put("size", 10)
                .put("url", "https://cdn.example.com/cat.png")
                .put("proxy_url", "https://media.example.com/cat.png")
                .put("content_type", "image/png")
                .put("width", 10)
                .put("height", 10);
    }

    private static DataObject sticker() {
        return DataObject.empty()
                .put("id", "620000000000000006")
                .put("name", "wave")
                .put("format_type", 1);
    }

    private static DataObject reference(int type) {
        return DataObject.empty()
                .put("type", type)
                .put("message_id", "630000000000000006")
                .put("channel_id", Long.toString(CHANNEL_ID))
                .put("guild_id", Long.toString(GUILD_ID));
    }
}
