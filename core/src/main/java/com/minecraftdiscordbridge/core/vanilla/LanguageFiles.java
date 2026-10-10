package com.minecraftdiscordbridge.core.vanilla;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.minecraftdiscordbridge.core.storage.AtomicFile;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LanguageFiles {
    static final URI MANIFEST = URI.create("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json");
    static final String RESOURCES = "https://resources.download.minecraft.net/";
    private static final Logger LOGGER = LoggerFactory.getLogger("MinecraftDiscordBridge");
    private static final Pattern SHA1 = Pattern.compile("[0-9a-f]{40}");
    private static final Pattern UNSAFE = Pattern.compile("[^A-Za-z0-9._-]");

    private final Path directory;
    private final Fetcher fetcher;

    public LanguageFiles(Path dataDirectory, Fetcher fetcher) {
        this.directory = dataDirectory.resolve("data").resolve("cache").resolve("lang");
        this.fetcher = fetcher;
    }

    public Map<String, String> load(String version, String locale) throws IOException {
        Path pointer = directory.resolve(locale + "-" + UNSAFE.matcher(version).replaceAll("_") + ".sha1");
        Optional<byte[]> cached = cached(pointer);
        if (cached.isPresent()) {
            return parse(cached.get());
        }
        String hash = hash(version, locale);
        Optional<byte[]> stored = verified(file(hash), hash);
        byte[] bytes;
        if (stored.isPresent()) {
            bytes = stored.get();
        } else {
            LOGGER.info("Downloading Minecraft {}'s {} language file from Mojang", version, locale);
            bytes = download(URI.create(RESOURCES + hash.substring(0, 2) + "/" + hash), hash);
        }
        Map<String, String> patterns = parse(bytes);
        AtomicFile.write(file(hash), bytes);
        AtomicFile.write(pointer, hash.getBytes(StandardCharsets.US_ASCII));
        return patterns;
    }

    public void close() {
        fetcher.close();
    }

    private Optional<byte[]> cached(Path pointer) throws IOException {
        String hash;
        try {
            hash = Files.readString(pointer, StandardCharsets.US_ASCII).strip();
        } catch (NoSuchFileException missing) {
            return Optional.empty();
        }
        return SHA1.matcher(hash).matches() ? verified(file(hash), hash) : Optional.empty();
    }

    private String hash(String version, String locale) throws IOException {
        JsonObject manifest = json(fetcher.fetch(MANIFEST), MANIFEST);
        JsonObject details = linked(versionEntry(manifest, version));
        JsonObject index = linked(object(details, "assetIndex"));
        JsonElement asset = object(index, "objects").get("minecraft/lang/" + locale + ".json");
        if (asset == null || !asset.isJsonObject()) {
            throw new IOException("Minecraft " + version + " has no " + locale + " language file");
        }
        return sha1(asset.getAsJsonObject(), "hash");
    }

    private JsonObject linked(JsonObject reference) throws IOException {
        URI uri = uri(reference);
        return json(download(uri, sha1(reference, "sha1")), uri);
    }

    private Path file(String hash) {
        return directory.resolve(hash + ".json");
    }

    private byte[] download(URI uri, String expected) throws IOException {
        byte[] bytes = fetcher.fetch(uri);
        String actual = sha1(bytes);
        if (!actual.equals(expected)) {
            throw new IOException(
                    "The download from " + uri + " has SHA-1 " + actual + " but Mojang lists " + expected);
        }
        return bytes;
    }

    private static Optional<byte[]> verified(Path file, String hash) throws IOException {
        try {
            byte[] bytes = Files.readAllBytes(file);
            return sha1(bytes).equals(hash) ? Optional.of(bytes) : Optional.empty();
        } catch (NoSuchFileException missing) {
            return Optional.empty();
        }
    }

    private static JsonObject versionEntry(JsonObject manifest, String version) throws IOException {
        JsonElement versions = manifest.get("versions");
        if (versions != null && versions.isJsonArray()) {
            for (JsonElement candidate : versions.getAsJsonArray()) {
                if (candidate.isJsonObject() && version.equals(text(candidate.getAsJsonObject(), "id"))) {
                    return candidate.getAsJsonObject();
                }
            }
        }
        throw new IOException("Mojang's version list has no Minecraft " + version);
    }

    private static Map<String, String> parse(byte[] bytes) throws IOException {
        JsonObject root = json(bytes, null);
        Map<String, String> patterns = new HashMap<>();
        root.entrySet().forEach(entry -> {
            JsonElement value = entry.getValue();
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                patterns.put(entry.getKey(), value.getAsString());
            }
        });
        return Map.copyOf(patterns);
    }

    private static JsonObject json(byte[] bytes, @Nullable URI source) throws IOException {
        String from = source == null ? "the language file" : source.toString();
        try {
            JsonElement root = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
            if (!root.isJsonObject()) {
                throw new IOException(from + " is not a JSON object");
            }
            return root.getAsJsonObject();
        } catch (JsonParseException | IllegalStateException invalid) {
            throw new IOException(from + " is not valid JSON: " + invalid.getMessage(), invalid);
        }
    }

    private static JsonObject object(JsonObject parent, String name) throws IOException {
        JsonElement child = parent.get(name);
        if (child == null || !child.isJsonObject()) {
            throw new IOException("Mojang's metadata has no \"" + name + "\" object");
        }
        return child.getAsJsonObject();
    }

    private static @Nullable String text(JsonObject parent, String name) {
        JsonElement child = parent.get(name);
        return child != null && child.isJsonPrimitive() ? child.getAsString() : null;
    }

    private static URI uri(JsonObject parent) throws IOException {
        String value = text(parent, "url");
        try {
            URI uri = value == null ? null : new URI(value);
            if (uri == null || !"https".equals(uri.getScheme()) || uri.getHost() == null) {
                throw new IOException("Mojang's metadata has no https \"url\": " + value);
            }
            return uri;
        } catch (URISyntaxException invalid) {
            throw new IOException("Mojang's metadata has an invalid \"url\": " + value, invalid);
        }
    }

    private static String sha1(JsonObject parent, String name) throws IOException {
        String value = text(parent, name);
        if (value == null || !SHA1.matcher(value).matches()) {
            throw new IOException("Mojang's metadata has no SHA-1 in \"" + name + "\": " + value);
        }
        return value;
    }

    static String sha1(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bytes));
        } catch (NoSuchAlgorithmException missing) {
            throw new IllegalStateException("This Java runtime has no SHA-1", missing);
        }
    }
}
