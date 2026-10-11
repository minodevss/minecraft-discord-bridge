package com.minecraftdiscordbridge.core.discord.jda;

import static com.minecraftdiscordbridge.core.discord.jda.DiscordPayloads.ADMINISTRATOR;
import static com.minecraftdiscordbridge.core.discord.jda.DiscordPayloads.APPLICATION_ID;
import static com.minecraftdiscordbridge.core.discord.jda.DiscordPayloads.BOT_ID;
import static com.minecraftdiscordbridge.core.discord.jda.DiscordPayloads.CHANNEL_ID;
import static com.minecraftdiscordbridge.core.discord.jda.DiscordPayloads.GUILD_ID;
import static com.minecraftdiscordbridge.core.discord.jda.DiscordPayloads.MESSAGE_CONTENT_INTENT;
import static com.minecraftdiscordbridge.core.discord.jda.DiscordPayloads.PLAYER_ID;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
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

final class FakeDiscordServer implements AutoCloseable {
    private static final String API = "/api/v10/";

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
    volatile boolean holdWebhookAnswers;
    volatile boolean botManagesRoles;
    final AtomicInteger webhookGoneResponses = new AtomicInteger();
    final AtomicInteger rateLimitedResponses = new AtomicInteger();
    private final MockWebServer server = new MockWebServer();
    private static final long DISCORD_EPOCH = 1420070400000L;
    private final AtomicLong ids = new AtomicLong((System.currentTimeMillis() - DISCORD_EPOCH) << 22);
    private final CountDownLatch released = new CountDownLatch(1);
    private final CountDownLatch webhookAnswers = new CountDownLatch(1);
    private final AtomicInteger sequence = new AtomicInteger();
    private final List<WebSocket> sockets = new CopyOnWriteArrayList<>();
    private volatile @Nullable WebSocket socket;

    FakeDiscordServer() throws IOException {
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
                restBaseUrl(), Optional.of("ws://" + server.getHostName() + ":" + server.getPort() + "/gateway"));
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

    DataObject message(long authorId, String content) {
        return DiscordPayloads.message(ids.incrementAndGet(), authorId, content);
    }

    DataObject command(long guildId, String name, int type, DataArray options) {
        return DiscordPayloads.command(ids.incrementAndGet(), guildId, name, type, options);
    }

    void sendMessage(DataObject message) {
        dispatch("MESSAGE_CREATE", message);
    }

    void answerHeldWebhooks() {
        webhookAnswers.countDown();
    }

    @Override
    public void close() throws IOException {
        released.countDown();
        webhookAnswers.countDown();
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
            return stallLogin ? stalled() : json(DiscordPayloads.user(BOT_ID, "Bridge", true));
        }
        if (method.equals("GET") && route.equals("channels/" + CHANNEL_ID + "/webhooks")) {
            return json(DataArray.empty().toString());
        }
        if (method.equals("POST") && route.equals("channels/" + CHANNEL_ID + "/webhooks")) {
            return json(DiscordPayloads.webhook(ids.incrementAndGet()).toString());
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
            return json(DiscordPayloads.channel().toString());
        }
        String members = "guilds/" + GUILD_ID + "/members/";
        if (method.equals("GET") && route.equals(members + PLAYER_ID)) {
            return json(DiscordPayloads.member("Alex")
                    .put("user", DataObject.fromJson(DiscordPayloads.user(PLAYER_ID, "Alex", false)))
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
            applicationFlags = DiscordPayloads.body(request).getLong("flags");
        }
        return json(DiscordPayloads.application(applicationFlags).toString());
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
        if (holdWebhookAnswers) {
            await(webhookAnswers);
        }
        String rest = route.substring("webhooks/".length());
        long webhookId = Long.parseLong(rest.substring(0, rest.indexOf('/')));
        return json(message(webhookId, "sent")
                .put("webhook_id", Long.toString(webhookId))
                .toString());
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
        await(released);
        return error(503, 0, "Stalled");
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
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
            timeline.add("GATEWAY close");
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
                    .put("user", DataObject.fromJson(DiscordPayloads.user(BOT_ID, "Bridge", true)))
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
                dispatch("GUILD_CREATE", DiscordPayloads.guild(botManagesRoles, everyonePermissions));
            }
        }
    }
}
