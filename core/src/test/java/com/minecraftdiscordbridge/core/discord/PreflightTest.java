package com.minecraftdiscordbridge.core.discord;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import net.dv8tion.jda.api.utils.data.DataObject;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PreflightTest {
    private static final long MESSAGE_CONTENT_LIMITED = 1L << 19;

    private MockWebServer server;
    private OkHttpClient http;
    private Preflight preflight;

    @BeforeEach
    void startServer() throws Exception {
        server = new MockWebServer();
        server.start();
        http = HttpClients.create(new RateLimitCounter());
        preflight = new Preflight(http, server.url("/api/v10/").toString());
    }

    @AfterEach
    void stopServer() throws Exception {
        HttpClients.close(http);
        server.close();
    }

    @Test
    void readsTheApplicationWithTheBotToken() throws Exception {
        server.enqueue(application(1L << 18));

        PreflightResult result = preflight.run("secret", true);

        assertThat(result).isInstanceOf(PreflightResult.Ready.class);
        assertThat(((PreflightResult.Ready) result).application().id()).isEqualTo(42);
        RecordedRequest request = server.takeRequest();
        assertThat(request.getMethod()).isEqualTo("GET");
        assertThat(request.getUrl().encodedPath()).isEqualTo("/api/v10/applications/@me");
        assertThat(request.getHeaders().get("Authorization")).isEqualTo("Bot secret");
        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test
    void reportsAnInvalidTokenOn401() {
        server.enqueue(new MockResponse.Builder()
                .code(401)
                .body("{\"message\":\"401: Unauthorized\",\"code\":0}")
                .build());

        assertThat(preflight.run("wrong", true)).isInstanceOf(PreflightResult.InvalidToken.class);
    }

    @Test
    void reportsAnInvalidTokenThatCannotBeSentAsAHeader() {
        assertThat(preflight.run("bad\ntoken", true)).isInstanceOf(PreflightResult.InvalidToken.class);
        assertThat(server.getRequestCount()).isZero();
    }

    @Test
    void reportsDiscordAsUnreachableOnServerErrors() {
        server.enqueue(new MockResponse.Builder().code(502).build());

        assertThat(preflight.run("secret", true)).isInstanceOf(PreflightResult.Unreachable.class);
    }

    @Test
    void turnsOnMessageContentAndKeepsEveryOtherFlag() throws Exception {
        long otherFlags = (1L << 12) | (1L << 15) | (1L << 23);
        server.enqueue(application(otherFlags));
        server.enqueue(application(otherFlags | MESSAGE_CONTENT_LIMITED));

        PreflightResult result = preflight.run("secret", true);

        server.takeRequest();
        RecordedRequest patch = server.takeRequest(1, TimeUnit.SECONDS);
        assertThat(patch.getMethod()).isEqualTo("PATCH");
        assertThat(patch.getHeaders().get("Authorization")).isEqualTo("Bot secret");
        assertThat(DataObject.fromJson(patch.getBody().utf8()).getLong("flags"))
                .isEqualTo(otherFlags | MESSAGE_CONTENT_LIMITED);
        assertThat(((PreflightResult.Ready) result).application().messageContentGranted())
                .isTrue();
    }

    @Test
    void leavesFlagsAloneWhenMessageContentIsAlreadyOn() {
        server.enqueue(application(1L << 18));

        preflight.run("secret", true);

        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test
    void leavesFlagsAloneWhenNotAllowedToChangeThem() {
        server.enqueue(application(0));

        PreflightResult result = preflight.run("secret", false);

        assertThat(server.getRequestCount()).isEqualTo(1);
        assertThat(((PreflightResult.Ready) result).application().messageContentGranted())
                .isFalse();
    }

    @Test
    void carriesOnWithoutMessageContentWhenDiscordRefusesTheChange() {
        server.enqueue(application(0));
        server.enqueue(new MockResponse.Builder()
                .code(403)
                .body("{\"message\":\"Missing Access\",\"code\":50001}")
                .build());

        PreflightResult result = preflight.run("secret", true);

        assertThat(server.getRequestCount()).isEqualTo(2);
        assertThat(((PreflightResult.Ready) result).application().messageContentGranted())
                .isFalse();
    }

    private static MockResponse application(long flags) {
        return new MockResponse.Builder()
                .code(200)
                .setHeader("Content-Type", "application/json")
                .body("{\"id\":\"42\",\"flags\":" + flags
                        + ",\"bot_public\":false,\"owner\":{\"id\":\"7\"},\"team\":null}")
                .build();
    }
}
