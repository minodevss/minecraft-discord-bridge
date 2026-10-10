package com.minecraftdiscordbridge.core.vanilla;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class FakeFetcher implements Fetcher {
    public final List<URI> requests = new CopyOnWriteArrayList<>();
    private final Map<URI, byte[]> responses = new ConcurrentHashMap<>();
    private final List<String> versions = new CopyOnWriteArrayList<>();

    public FakeFetcher serve(String version, String locale, String languageJson) {
        return serve(version, locale, languageJson, sha1(languageJson));
    }

    public FakeFetcher serve(String version, String locale, String languageJson, String listedHash) {
        responses.put(languageUri(listedHash), bytes(languageJson));
        String index = "{\"objects\":{\"minecraft/sounds.json\":{\"hash\":\"" + "0".repeat(40) + "\",\"size\":1},"
                + "\"minecraft/lang/" + locale + ".json\":{\"hash\":\"" + listedHash + "\",\"size\":"
                + bytes(languageJson).length + "}}}";
        URI indexUri = packageUri(index, version + "-assets.json");
        responses.put(indexUri, bytes(index));
        String details = "{\"id\":\"" + version + "\",\"assetIndex\":{\"id\":\"17\",\"sha1\":\"" + sha1(index)
                + "\",\"url\":\"" + indexUri + "\"}}";
        URI detailsUri = packageUri(details, version + ".json");
        responses.put(detailsUri, bytes(details));
        versions.add("{\"id\":\"" + version + "\",\"type\":\"release\",\"url\":\"" + detailsUri + "\",\"sha1\":\""
                + sha1(details) + "\"}");
        responses.put(
                LanguageFiles.MANIFEST, bytes("{\"latest\":{},\"versions\":[" + String.join(",", versions) + "]}"));
        return this;
    }

    public static URI languageUri(String hash) {
        return URI.create(LanguageFiles.RESOURCES + hash.substring(0, 2) + "/" + hash);
    }

    public static String sha1(String text) {
        return LanguageFiles.sha1(bytes(text));
    }

    public final CountDownLatch released = new CountDownLatch(0);
    public volatile CountDownLatch hold = released;

    @Override
    public byte[] fetch(URI uri) throws IOException {
        requests.add(uri);
        try {
            if (!hold.await(30, TimeUnit.SECONDS)) {
                throw new IOException("Held too long: " + uri);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted: " + uri, interrupted);
        }
        byte[] body = responses.get(uri);
        if (body == null) {
            throw new IOException("No network in tests: " + uri);
        }
        return body;
    }

    private static URI packageUri(String body, String name) {
        return URI.create("https://piston-meta.mojang.com/v1/packages/" + sha1(body) + "/" + name);
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
