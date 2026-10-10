package com.minecraftdiscordbridge.core.vanilla;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.minecraftdiscordbridge.core.Waits;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HttpFetcherTest {
    private final MockWebServer server = new MockWebServer();
    private final HttpFetcher fetcher = new HttpFetcher();

    @BeforeEach
    void start() throws IOException {
        server.start();
    }

    @AfterEach
    void stop() {
        fetcher.close();
        server.close();
    }

    @Test
    void returnsTheBodyOfAnOkResponse() throws IOException {
        server.enqueue(new MockResponse.Builder().body("{\"objects\":{}}").build());

        assertThat(fetcher.fetch(server.url("/17.json").uri())).asString().isEqualTo("{\"objects\":{}}");
    }

    @Test
    void rejectsOtherStatusCodes() {
        server.enqueue(new MockResponse.Builder().code(404).build());

        assertThatThrownBy(() -> fetcher.fetch(server.url("/missing").uri()))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("HTTP 404");
    }

    @Test
    void stopsWaitingAsSoonAsItIsClosed() {
        server.enqueue(
                new MockResponse.Builder().headersDelay(10, TimeUnit.SECONDS).build());
        CompletableFuture<byte[]> pending = CompletableFuture.supplyAsync(() -> {
            try {
                return fetcher.fetch(server.url("/slow").uri());
            } catch (IOException failure) {
                throw new CompletionException(failure);
            }
        });
        long started = System.nanoTime();

        Waits.until("the request", () -> server.getRequestCount() > 0);
        fetcher.close();

        assertThatThrownBy(pending::join).hasCauseInstanceOf(IOException.class);
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(5));
    }
}
