package com.minecraftdiscordbridge.core.config;

import com.minecraftdiscordbridge.core.config.RawConfig.Entry;
import com.minecraftdiscordbridge.core.storage.AtomicFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class SecretsReader {
    static final String FILE = "secrets.yml";
    static final String TOKEN_VARIABLE = "MCDB_DISCORD_TOKEN";
    static final String TOKEN_FILE_VARIABLE = "MCDB_DISCORD_TOKEN_FILE";
    static final String TOKEN_KEY = "discord-token";
    static final String TEMPLATE = """
            # Keep this file private: anyone with the token controls your bot.
            # Copy the token from the Discord Developer Portal (your application > Bot > Reset Token).
            # The MCDB_DISCORD_TOKEN or MCDB_DISCORD_TOKEN_FILE environment variable overrides this file.
            discord-token: ""
            """;

    private static final Logger LOGGER = LoggerFactory.getLogger("MinecraftDiscordBridge");

    private SecretsReader() {}

    static Secrets read(Path directory, Map<String, String> environment, Problems problems) {
        Path file = directory.resolve(FILE);
        createIfMissing(file, problems);
        String token = environment.getOrDefault(TOKEN_VARIABLE, "").strip();
        String tokenFile = environment.getOrDefault(TOKEN_FILE_VARIABLE, "").strip();
        if (!token.isEmpty()) {
            if (!tokenFile.isEmpty()) {
                problems.forSource(TOKEN_FILE_VARIABLE)
                        .warning(null, "", "ignored because " + TOKEN_VARIABLE + " is also set. Unset one of them.");
            }
            return Secrets.of(token, TOKEN_VARIABLE);
        }
        if (!tokenFile.isEmpty()) {
            return fromTokenFile(tokenFile, problems.forSource(TOKEN_FILE_VARIABLE));
        }
        return fromSecretsFile(file, problems.forSource(FILE));
    }

    private static void createIfMissing(Path file, Problems problems) {
        if (Files.exists(file)) {
            return;
        }
        try {
            AtomicFile.writeOwnerOnly(file, TEMPLATE.getBytes(StandardCharsets.UTF_8));
            LOGGER.info("Created {}. Put the Discord bot token in it, then restart the server.", file);
        } catch (IOException failure) {
            problems.forSource(FILE)
                    .warning(
                            null,
                            "",
                            "could not create " + file + " (" + failure.getMessage()
                                    + "). Check that the folder is writable, or set " + TOKEN_VARIABLE + ".");
        }
    }

    private static Secrets fromTokenFile(String location, Problems problems) {
        try {
            Path path = Path.of(location);
            String token = Files.readString(path).strip();
            if (token.isEmpty()) {
                problems.error(null, "", path + " is empty. Put the Discord bot token in it.");
            }
            return Secrets.of(token, TOKEN_FILE_VARIABLE);
        } catch (IOException | InvalidPathException failure) {
            problems.error(
                    null,
                    "",
                    "cannot read " + location + " (" + failure.getClass().getSimpleName()
                            + "). Check the path and the file permissions.");
            return Secrets.none();
        }
    }

    private static Secrets fromSecretsFile(Path file, Problems problems) {
        String text;
        try {
            text = Files.readString(file);
        } catch (NoSuchFileException missing) {
            return Secrets.none();
        } catch (IOException failure) {
            problems.error(
                    null,
                    "",
                    "cannot read " + file + " (" + failure.getClass().getSimpleName()
                            + "). Check the file permissions and that it is saved as UTF-8.");
            return Secrets.none();
        }
        RawConfig raw = RawConfig.parse(text, Set.of(TOKEN_KEY), problems).orElse(null);
        if (raw == null) {
            return Secrets.none();
        }
        for (String path : raw.paths()) {
            if (!path.equals(TOKEN_KEY)) {
                Location at = raw.get(path).map(Entry::keyAt).orElse(null);
                problems.warning(at, path, Suggestions.unknownKey(path, List.of(TOKEN_KEY)));
            }
        }
        Optional<Entry> entry = raw.get(TOKEN_KEY);
        if (entry.isEmpty()) {
            return Secrets.none();
        }
        String token = ValueTypes.TEXT_OR_NOTHING.read(entry.get().value(), TOKEN_KEY, problems);
        return token == null ? Secrets.none() : Secrets.of(token.strip(), FILE);
    }
}
