package com.minecraftdiscordbridge.core.vanilla;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.jspecify.annotations.Nullable;

public final class HttpFetcher implements Fetcher {
    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration GIVE_UP = CONNECT_TIMEOUT.plus(REQUEST_TIMEOUT);

    private volatile @Nullable CompletableFuture<?> inFlight;
    private @Nullable HttpClient client;
    private volatile boolean closed;

    @Override
    public byte[] fetch(URI uri) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("User-Agent", "MinecraftDiscordBridge")
                .GET()
                .build();
        CompletableFuture<HttpResponse<byte[]>> pending =
                client().sendAsync(request, HttpResponse.BodyHandlers.ofByteArray());
        inFlight = pending;
        try {
            if (closed) {
                pending.cancel(true);
            }
            HttpResponse<byte[]> response = pending.get(GIVE_UP.toMillis(), TimeUnit.MILLISECONDS);
            if (response.statusCode() != 200) {
                throw new IOException(uri.getHost() + " answered HTTP " + response.statusCode() + " for " + uri);
            }
            return response.body();
        } catch (ExecutionException failed) {
            Throwable cause = failed.getCause() == null ? failed : failed.getCause();
            throw cause instanceof IOException io ? io : new IOException(cause.toString(), cause);
        } catch (TimeoutException late) {
            pending.cancel(true);
            throw new HttpTimeoutException(uri.getHost() + " did not answer within " + GIVE_UP.toSeconds() + " s");
        } catch (CancellationException cancelled) {
            throw new IOException("Stopped downloading " + uri + " because the bridge is stopping");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            pending.cancel(true);
            throw new InterruptedIOException("Interrupted while downloading " + uri);
        } finally {
            inFlight = null;
        }
    }

    @Override
    public void close() {
        closed = true;
        CompletableFuture<?> pending = inFlight;
        if (pending != null) {
            pending.cancel(true);
        }
    }

    private synchronized HttpClient client() {
        HttpClient current = client;
        if (current == null) {
            current = HttpClient.newBuilder()
                    .connectTimeout(CONNECT_TIMEOUT)
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
            client = current;
        }
        return current;
    }
}
