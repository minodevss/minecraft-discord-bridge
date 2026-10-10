package com.minecraftdiscordbridge.core.discord;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import mockwebserver3.Dispatcher;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import net.dv8tion.jda.api.utils.data.DataArray;
import net.dv8tion.jda.api.utils.data.DataObject;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.jspecify.annotations.Nullable;

final class FakeDiscord implements AutoCloseable {
    static final String TOKEN = "test-token";
    static final long APPLICATION_ID = 100000000000000001L;
    static final long BOT_ID = APPLICATION_ID;
    static final long GUILD_ID = 200000000000000002L;
    static final long OTHER_GUILD_ID = 200000000000000099L;
    static final long CHANNEL_ID = 300000000000000003L;
    static final long ROLE_ID = 500000000000000005L;
    static final long BOT_ROLE_ID = 500000000000000055L;
    static final long PLAYER_ID = 400000000000000004L;
    static final long FRIEND_ID = 400000000000000044L;
    static final long OTHER_BOT_ID = 400000000000000444L;
    static final long FOREIGN_WEBHOOK_ID = 700000000000000007L;
    static final int MESSAGE_CONTENT_INTENT = 1 << 15;
    static final String ADMINISTRATOR = "8";
    static final String NO_WEBHOOKS = "68608";
    private static final String API = "/api/v10/";
    private static final String TIMESTAMP = "2026-10-10T00:00:00.000000+00:00";

    final List<String> timeline = new CopyOnWriteArrayList<>();
    final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();
    final List<DataObject> identifies = new CopyOnWriteArrayList<>();
    final List<DataObject> presences = new CopyOnWriteArrayList<>();
    volatile long applicationFlags = ApplicationInfo.GATEWAY_MESSAGE_CONTENT_LIMITED;
    volatile boolean tokenValid = true;
    volatile boolean patchAllowed = true;
    volatile boolean refuseMessageContent;
    volatile boolean stallMessages;
    volatile boolean guildMember = true;
    volatile String everyonePermissions = ADMINISTRATOR;
    volatile boolean stallLogin;
    volatile boolean botManagesRoles;
    final AtomicInteger webhookGoneResponses = new AtomicInteger();
    final AtomicInteger rateLimitedResponses = new AtomicInteger();
    private final MockWebServer server = new MockWebServer();
    private static final long DISCORD_EPOCH = 1420070400000L;
    private final AtomicLong ids = new AtomicLong((System.currentTimeMillis() - DISCORD_EPOCH) << 22);
    private final CountDownLatch released = new CountDownLatch(1);
    private final AtomicInteger sequence = new AtomicInteger();
    private final List<WebSocket> sockets = new CopyOnWriteArrayList<>();
    private volatile @Nullable WebSocket socket;

    FakeDiscord() throws IOException {
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                return respond(request);
            }
        });
        server.start();
    }

    JdaDiscord.Endpoints endpoints() {
        return new JdaDiscord.Endpoints(
                restBaseUrl(), "ws://" + server.getHostName() + ":" + server.getPort() + "/gateway");
    }

    String restBaseUrl() {
        return server.url(API).toString();
    }

    List<RecordedRequest> requests(String method, String route) {
        return requests.stream()
                .filter(request ->
                        request.getMethod().equals(method) && route(request).equals(route))
                .toList();
    }

    List<RecordedRequest> requestsStartingWith(String method, String prefix) {
        return requests.stream()
                .filter(request ->
                        request.getMethod().equals(method) && route(request).startsWith(prefix))
                .toList();
    }

    static DataObject body(RecordedRequest request) {
        return DataObject.fromJson(request.getBody().utf8());
    }

    void dispatch(String type, DataObject data) {
        WebSocket current = socket;
        if (current == null) {
            throw new IllegalStateException("No gateway connection");
        }
        current.send(DataObject.empty()
                .put("op", 0)
                .put("t", type)
                .put("s", sequence.incrementAndGet())
                .put("d", data)
                .toString());
    }

    void sendMessage(DataObject message) {
        dispatch("MESSAGE_CREATE", message);
    }

    @Override
    public void close() throws IOException {
        released.countDown();
        sockets.forEach(open -> open.close(1000, null));
        server.close();
    }

    private MockResponse respond(RecordedRequest request) {
        requests.add(request);
        String route = route(request);
        String method = request.getMethod();
        timeline.add(method + " " + route);
        if (request.getUrl().encodedPath().equals("/gateway")) {
            return new MockResponse.Builder().webSocketUpgrade(new Gateway()).build();
        }
        if (route.equals("applications/@me")) {
            return application(method, request);
        }
        if (method.equals("GET") && route.equals("users/@me")) {
            return stallLogin ? stalled() : json(user(BOT_ID, "Bridge", true));
        }
        if (method.equals("GET") && route.equals("channels/" + CHANNEL_ID + "/webhooks")) {
            return json(DataArray.empty().toString());
        }
        if (method.equals("POST") && route.equals("channels/" + CHANNEL_ID + "/webhooks")) {
            return json(webhook(ids.incrementAndGet()).toString());
        }
        if (method.equals("POST") && route.startsWith("webhooks/")) {
            return executeWebhook(route);
        }
        if (method.equals("POST") && route.equals("channels/" + CHANNEL_ID + "/messages")) {
            return stallMessages ? stalled() : json(message(BOT_ID, "sent").toString());
        }
        if (method.equals("PUT") && route.startsWith("applications/")) {
            return json(DataArray.empty().toString());
        }
        if (method.equals("POST") && route.startsWith("interactions/")) {
            return new MockResponse.Builder().code(204).build();
        }
        if (method.equals("PATCH") && route.startsWith("webhooks/")) {
            return json(message(BOT_ID, "edited").toString());
        }
        if (method.equals("PATCH") && route.equals("channels/" + CHANNEL_ID)) {
            return json(channel().toString());
        }
        String members = "guilds/" + GUILD_ID + "/members/";
        if (method.equals("GET") && route.equals(members + PLAYER_ID)) {
            return json(member("Alex")
                    .put("user", DataObject.fromJson(user(PLAYER_ID, "Alex", false)))
                    .toString());
        }
        if (method.equals("GET") && route.startsWith(members)) {
            return error(404, 10007, "Unknown Member");
        }
        if ((method.equals("PUT") || method.equals("DELETE"))
                && route.startsWith(members)
                && route.contains("/roles/")) {
            return new MockResponse.Builder().code(204).build();
        }
        return error(404, 0, "Not found");
    }

    private MockResponse application(String method, RecordedRequest request) {
        if (!tokenValid) {
            return error(401, 0, "401: Unauthorized");
        }
        if (method.equals("PATCH")) {
            if (!patchAllowed) {
                return error(403, 50013, "Missing Permissions");
            }
            applicationFlags = body(request).getLong("flags");
        }
        DataObject team = DataObject.empty()
                .put("id", "600000000000000006")
                .put("owner_user_id", Long.toString(PLAYER_ID))
                .put(
                        "members",
                        DataArray.empty()
                                .add(teamMember(FRIEND_ID, "developer", 2))
                                .add(teamMember(OTHER_BOT_ID, "read_only", 2)));
        return json(DataObject.empty()
                .put("id", Long.toString(APPLICATION_ID))
                .put("name", "Bridge")
                .put("flags", applicationFlags)
                .put("bot_public", false)
                .put("owner", DataObject.fromJson(user(PLAYER_ID, "Owner", false)))
                .put("team", team)
                .toString());
    }

    private MockResponse executeWebhook(String route) {
        if (webhookGoneResponses.getAndDecrement() > 0) {
            return error(404, 10015, "Unknown Webhook");
        }
        if (rateLimitedResponses.getAndDecrement() > 0) {
            return new MockResponse.Builder()
                    .code(429)
                    .setHeader("Content-Type", "application/json")
                    .setHeader("Retry-After", "0")
                    .setHeader("X-RateLimit-Scope", "user")
                    .setHeader("via", "1.1 google")
                    .body("{\"message\":\"You are being rate limited.\",\"retry_after\":0.0,\"global\":false}")
                    .build();
        }
        if (stallMessages) {
            return stalled();
        }
        String rest = route.substring("webhooks/".length());
        long webhookId = Long.parseLong(rest.substring(0, rest.indexOf('/')));
        return json(message(webhookId, "sent")
                .put("webhook_id", Long.toString(webhookId))
                .toString());
    }

    DataObject message(long authorId, String content) {
        return DataObject.empty()
                .put("id", Long.toString(ids.incrementAndGet()))
                .put("channel_id", Long.toString(CHANNEL_ID))
                .put("guild_id", Long.toString(GUILD_ID))
                .put("author", DataObject.fromJson(user(authorId, "Author" + authorId, false)))
                .put("content", content)
                .put("timestamp", TIMESTAMP)
                .put("edited_timestamp", null)
                .put("tts", false)
                .put("mention_everyone", false)
                .put("mentions", DataArray.empty())
                .put("mention_roles", DataArray.empty())
                .put("attachments", DataArray.empty())
                .put("embeds", DataArray.empty())
                .put("components", DataArray.empty())
                .put("pinned", false)
                .put("type", 0)
                .put("flags", 0);
    }

    DataObject command(long guildId, String name, int type, DataArray options) {
        DataObject member = member("Alex")
                .put("user", DataObject.fromJson(user(PLAYER_ID, "Alex", false)))
                .put("permissions", "8");
        return DataObject.empty()
                .put("id", Long.toString(ids.incrementAndGet()))
                .put("application_id", Long.toString(APPLICATION_ID))
                .put("type", type)
                .put("token", "interaction-token")
                .put("version", 1)
                .put("guild_id", Long.toString(guildId))
                .put(
                        "guild",
                        DataObject.empty()
                                .put("id", Long.toString(guildId))
                                .put("locale", "en-US")
                                .put("features", DataArray.empty()))
                .put("channel_id", Long.toString(CHANNEL_ID))
                .put(
                        "channel",
                        channel().put("guild_id", Long.toString(guildId)).put("permissions", "8"))
                .put("member", member)
                .put("locale", "ko")
                .put("guild_locale", "en-US")
                .put("app_permissions", "8")
                .put("entitlements", DataArray.empty())
                .put("authorizing_integration_owners", DataObject.empty().put("0", Long.toString(guildId)))
                .put("context", 0)
                .put(
                        "data",
                        DataObject.empty()
                                .put("id", "800000000000000008")
                                .put("name", name)
                                .put("type", 1)
                                .put("options", options));
    }

    static DataObject member(String nick) {
        return DataObject.empty()
                .put("nick", nick)
                .put("roles", DataArray.empty().add(Long.toString(ROLE_ID)))
                .put("joined_at", TIMESTAMP)
                .put("deaf", false)
                .put("mute", false)
                .put("flags", 0);
    }

    static String user(long id, String name, boolean bot) {
        return DataObject.empty()
                .put("id", Long.toString(id))
                .put("username", name.toLowerCase(java.util.Locale.ROOT))
                .put("global_name", name)
                .put("discriminator", "0")
                .put("avatar", null)
                .put("bot", bot)
                .put("public_flags", 0)
                .toString();
    }

    DataObject channel() {
        return DataObject.empty()
                .put("id", Long.toString(CHANNEL_ID))
                .put("type", 0)
                .put("guild_id", Long.toString(GUILD_ID))
                .put("name", "minecraft")
                .put("position", 0)
                .put("permission_overwrites", DataArray.empty())
                .put("topic", null)
                .put("nsfw", false)
                .put("parent_id", null)
                .put("rate_limit_per_user", 0)
                .put("last_message_id", null);
    }

    private DataObject guild() {
        DataArray botRoles = botManagesRoles ? DataArray.empty().add(Long.toString(BOT_ROLE_ID)) : DataArray.empty();
        DataArray roles = DataArray.empty()
                .add(role(GUILD_ID, "@everyone", 0, everyonePermissions))
                .add(role(ROLE_ID, "Builders", 0x3498db, "0"));
        if (botManagesRoles) {
            roles.add(role(BOT_ROLE_ID, "Bridge", 0, Long.toString(1L << 28)));
        }
        DataObject bot = DataObject.empty()
                .put("user", DataObject.fromJson(user(BOT_ID, "Bridge", true)))
                .put("roles", botRoles)
                .put("joined_at", TIMESTAMP)
                .put("deaf", false)
                .put("mute", false)
                .put("flags", 0);
        return DataObject.empty()
                .put("id", Long.toString(GUILD_ID))
                .put("name", "Test Server")
                .put("icon", null)
                .put("owner_id", Long.toString(PLAYER_ID))
                .put("unavailable", false)
                .put("member_count", 2)
                .put("large", false)
                .put("roles", roles)
                .put("channels", DataArray.empty().add(channel()))
                .put("threads", DataArray.empty())
                .put("guild_scheduled_events", DataArray.empty())
                .put("emojis", DataArray.empty())
                .put("stickers", DataArray.empty())
                .put("soundboard_sounds", DataArray.empty())
                .put("voice_states", DataArray.empty())
                .put("presences", DataArray.empty())
                .put("features", DataArray.empty())
                .put("members", DataArray.empty().add(bot))
                .put("premium_tier", 0)
                .put("verification_level", 0)
                .put("default_message_notifications", 0)
                .put("explicit_content_filter", 0)
                .put("mfa_level", 0)
                .put("nsfw_level", 0)
                .put("afk_timeout", 300)
                .put("system_channel_flags", 0)
                .put("premium_progress_bar_enabled", false)
                .put("preferred_locale", "en-US");
    }

    private static DataObject role(long id, String name, int color, String permissions) {
        return DataObject.empty()
                .put("id", Long.toString(id))
                .put("name", name)
                .put("color", color)
                .put(
                        "colors",
                        DataObject.empty()
                                .put("primary_color", color)
                                .put("secondary_color", null)
                                .put("tertiary_color", null))
                .put("hoist", false)
                .put("position", id == GUILD_ID ? 0 : id == BOT_ROLE_ID ? 2 : 1)
                .put("permissions", permissions)
                .put("managed", false)
                .put("mentionable", true)
                .put("flags", 0);
    }

    private DataObject webhook(long id) {
        return DataObject.empty()
                .put("id", Long.toString(id))
                .put("type", 1)
                .put("guild_id", Long.toString(GUILD_ID))
                .put("channel_id", Long.toString(CHANNEL_ID))
                .put("name", WebhookSender.WEBHOOK_NAME)
                .put("avatar", null)
                .put("token", "webhook-token-" + id)
                .put("application_id", Long.toString(APPLICATION_ID))
                .put("user", DataObject.fromJson(user(BOT_ID, "Bridge", true)));
    }

    private static DataObject teamMember(long userId, String role, int state) {
        return DataObject.empty()
                .put("membership_state", state)
                .put("team_id", "600000000000000006")
                .put("role", role)
                .put("user", DataObject.fromJson(user(userId, "Member" + userId, false)));
    }

    private static MockResponse json(String body) {
        return new MockResponse.Builder()
                .code(200)
                .setHeader("Content-Type", "application/json")
                .body(body)
                .build();
    }

    private static MockResponse error(int status, int code, String message) {
        return new MockResponse.Builder()
                .code(status)
                .setHeader("Content-Type", "application/json")
                .body(DataObject.empty()
                        .put("message", message)
                        .put("code", code)
                        .toString())
                .build();
    }

    private MockResponse stalled() {
        try {
            released.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        return error(503, 0, "Stalled");
    }

    private static String route(RecordedRequest request) {
        String path = request.getUrl().encodedPath();
        return path.startsWith(API) ? path.substring(API.length()) : path;
    }

    private final class Gateway extends WebSocketListener {
        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            socket = webSocket;
            sockets.add(webSocket);
            timeline.add("GATEWAY connect");
            webSocket.send(DataObject.empty()
                    .put("op", 10)
                    .put("d", DataObject.empty().put("heartbeat_interval", 41250))
                    .toString());
        }

        @Override
        public void onMessage(WebSocket webSocket, String text) {
            DataObject payload = DataObject.fromJson(text);
            switch (payload.getInt("op")) {
                case 1 -> webSocket.send("{\"op\":11}");
                case 2 -> identify(webSocket, payload.getObject("d"));
                case 3 -> presences.add(payload.getObject("d"));
                default -> {}
            }
        }

        @Override
        public void onClosing(WebSocket webSocket, int code, String reason) {
            webSocket.close(code, null);
        }

        private void identify(WebSocket webSocket, DataObject identify) {
            identifies.add(identify);
            int intents = identify.getInt("intents");
            timeline.add("IDENTIFY " + intents);
            if (refuseMessageContent && (intents & MESSAGE_CONTENT_INTENT) != 0) {
                webSocket.close(4014, "Disallowed intent(s).");
                return;
            }
            DataArray guilds = DataArray.empty();
            if (guildMember) {
                guilds.add(DataObject.empty().put("id", Long.toString(GUILD_ID)).put("unavailable", true));
            }
            DataObject ready = DataObject.empty()
                    .put("v", 10)
                    .put("user", DataObject.fromJson(user(BOT_ID, "Bridge", true)))
                    .put("guilds", guilds)
                    .put("session_id", "session-" + identifies.size())
                    .put("resume_gateway_url", "ws://" + server.getHostName() + ":" + server.getPort() + "/gateway")
                    .put("private_channels", DataArray.empty())
                    .put(
                            "application",
                            DataObject.empty()
                                    .put("id", Long.toString(APPLICATION_ID))
                                    .put("flags", applicationFlags));
            dispatch("READY", ready);
            if (guildMember) {
                dispatch("GUILD_CREATE", guild());
            }
        }
    }
}
