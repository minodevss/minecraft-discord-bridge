package com.minecraftdiscordbridge.core.discord.jda;

import static com.minecraftdiscordbridge.core.discord.jda.DiscordPayloads.APPLICATION_ID;
import static com.minecraftdiscordbridge.core.discord.jda.DiscordPayloads.CHANNEL_ID;
import static com.minecraftdiscordbridge.core.discord.jda.DiscordPayloads.FRIEND_ID;
import static com.minecraftdiscordbridge.core.discord.jda.DiscordPayloads.GUILD_ID;
import static com.minecraftdiscordbridge.core.discord.jda.DiscordPayloads.OTHER_BOT_ID;
import static com.minecraftdiscordbridge.core.discord.jda.DiscordPayloads.PLAYER_ID;
import static com.minecraftdiscordbridge.core.discord.jda.DiscordPayloads.ROLE_ID;
import static com.minecraftdiscordbridge.core.fakes.Waits.until;

import com.minecraftdiscordbridge.core.discord.Author;
import com.minecraftdiscordbridge.core.discord.CommandSpec;
import com.minecraftdiscordbridge.core.discord.DiscordId;
import com.minecraftdiscordbridge.core.discord.DiscordSettings;
import com.minecraftdiscordbridge.core.discord.DiscordStatus.State;
import com.minecraftdiscordbridge.core.discord.jda.TestSupport.MemoryCommandHashes;
import com.minecraftdiscordbridge.core.discord.jda.TestSupport.MemoryWebhooks;
import com.minecraftdiscordbridge.core.discord.jda.TestSupport.RecordingListener;
import com.minecraftdiscordbridge.core.loop.CoreLoop;
import com.minecraftdiscordbridge.core.loop.Deadline;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import mockwebserver3.RecordedRequest;
import net.dv8tion.jda.api.utils.data.DataObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

abstract class JdaTestBase {
    static final DiscordId CHANNEL = new DiscordId(CHANNEL_ID);
    static final DiscordId GUILD = new DiscordId(GUILD_ID);
    static final DiscordId PLAYER = new DiscordId(PLAYER_ID);
    static final DiscordId FRIEND = new DiscordId(FRIEND_ID);
    static final DiscordId ROLE = new DiscordId(ROLE_ID);
    static final DiscordId OTHER_BOT = new DiscordId(OTHER_BOT_ID);
    static final Author STEVE = Author.player("uuid-steve", "Steve", Optional.of("https://mc-heads.net/avatar/Steve"));
    static final Author SERVER = Author.server("Survival", Optional.of("https://example.com/server.png"));
    static final int GUILD_MEMBERS_INTENT = 1 << 1;
    static final Duration SHUTDOWN_BUDGET = Duration.ofMillis(2300);
    static final Duration SHUTDOWN_LIMIT = SHUTDOWN_BUDGET.plusMillis(250);
    static final CommandSpec LIST = new CommandSpec(
            "list",
            "Show who is online",
            Optional.of("접속 중인 플레이어"),
            List.of(new CommandSpec.Option(
                    CommandSpec.OptionType.INTEGER, "page", "Page", Optional.empty(), false, false)),
            CommandSpec.Access.EVERYONE,
            false,
            false);
    static final CommandSpec WHITELIST = new CommandSpec(
            "whitelist",
            "Add a player to the whitelist",
            Optional.empty(),
            List.of(new CommandSpec.Option(
                    CommandSpec.OptionType.STRING, "player", "Player name", Optional.of("플레이어 이름"), true, true)),
            CommandSpec.Access.ADMINS,
            true,
            false);

    FakeDiscordServer discord;
    CoreLoop loop;
    RecordingListener listener;
    MemoryWebhooks webhooks;
    MemoryCommandHashes commandHashes;
    JdaDiscord bridge;

    @BeforeEach
    void startFakeDiscord() throws Exception {
        discord = new FakeDiscordServer();
        loop = new CoreLoop();
        listener = new RecordingListener();
        webhooks = new MemoryWebhooks();
        commandHashes = new MemoryCommandHashes();
    }

    @AfterEach
    void stopEverything() throws Exception {
        if (bridge != null) {
            bridge.shutdown(List.of(), Deadline.after(SHUTDOWN_BUDGET));
        }
        loop.close();
        discord.close();
    }

    static DiscordSettings unbound(Set<DiscordId> channels, List<CommandSpec> commands) {
        DiscordSettings base = TestSupport.settings(commands);
        return new DiscordSettings(
                base.token(),
                Optional.empty(),
                channels,
                base.serverAuthor(),
                base.autoEnableMessageContent(),
                base.requestMembersIntent(),
                base.commands());
    }

    JdaDiscord startReady() {
        JdaDiscord started = start(TestSupport.settings(List.of()));
        until("ready", () -> started.status().state() == State.READY);
        return started;
    }

    JdaDiscord start(DiscordSettings settings) {
        JdaDiscord started = new JdaDiscord(settings, discord.endpoints(), loop, listener, webhooks, commandHashes);
        started.start();
        return started;
    }

    void awaitState(State state) {
        until(state.name(), () -> bridge.status().state() == state);
    }

    static DiscordSettings withAutoEnable(boolean autoEnable) {
        DiscordSettings base = TestSupport.settings(List.of());
        return new DiscordSettings(
                base.token(),
                base.guild(),
                base.channelIds(),
                base.serverAuthor(),
                autoEnable,
                base.requestMembersIntent(),
                base.commands());
    }

    List<RecordedRequest> executions() {
        return discord.requestsStartingWith("POST", "webhooks/");
    }

    List<RecordedRequest> botMessages() {
        return discord.requests("POST", "channels/" + CHANNEL_ID + "/messages");
    }

    List<RecordedRequest> registrations() {
        return discord.requestsStartingWith("PUT", "applications/" + APPLICATION_ID + "/guilds/" + GUILD_ID);
    }

    List<RecordedRequest> topicEdits() {
        return discord.requests("PATCH", "channels/" + CHANNEL_ID);
    }

    List<RecordedRequest> callbacks() {
        return discord.requestsStartingWith("POST", "interactions/");
    }

    List<RecordedRequest> originalEdits() {
        return discord.requestsStartingWith(
                "PATCH", "webhooks/" + APPLICATION_ID + "/interaction-token/messages/@original");
    }

    static DataObject attachment() {
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

    static DataObject sticker() {
        return DataObject.empty()
                .put("id", "620000000000000006")
                .put("name", "wave")
                .put("format_type", 1);
    }

    static DataObject reference(int type) {
        return DataObject.empty()
                .put("type", type)
                .put("message_id", "630000000000000006")
                .put("channel_id", Long.toString(CHANNEL_ID))
                .put("guild_id", Long.toString(GUILD_ID));
    }
}
