package com.minecraftdiscordbridge.core.config;

import com.minecraftdiscordbridge.core.storage.AtomicFile;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ConfigLoader {
    static final String FILE = "config.yml";

    private static final Logger LOGGER = LoggerFactory.getLogger("MinecraftDiscordBridge");

    private final Path directory;
    private final Map<String, String> environment;
    private final List<Migration> migrations;

    public ConfigLoader(Path directory, Map<String, String> environment) {
        this(directory, environment, ConfigSchema.MIGRATIONS);
    }

    ConfigLoader(Path directory, Map<String, String> environment, List<Migration> migrations) {
        this.directory = directory;
        this.environment = Map.copyOf(environment);
        this.migrations = migrations.stream()
                .sorted(Comparator.comparingInt(Migration::from))
                .toList();
    }

    public ConfigResult load() {
        Problems problems = new Problems(FILE);
        ConfigValues values = loadValues(problems);
        Secrets secrets = SecretsReader.read(directory, environment, problems);
        if (problems.hasErrors()) {
            return new ConfigResult.Failed(problems.errors(), problems.warnings());
        }
        return new ConfigResult.Loaded(new ConfigSnapshot(ConfigSchema.toConfig(values), secrets), problems.warnings());
    }

    private ConfigValues loadValues(Problems problems) {
        Path file = directory.resolve(FILE);
        byte[] original;
        try {
            original = Files.readAllBytes(file);
        } catch (NoSuchFileException missing) {
            createDefault(file, problems);
            return ConfigValues.defaults();
        } catch (IOException failure) {
            problems.error(
                    null,
                    "",
                    "cannot read " + file + " (" + failure.getClass().getSimpleName()
                            + "). Check the file permissions.");
            return ConfigValues.defaults();
        }
        String text = decode(original, problems);
        if (text == null) {
            return ConfigValues.defaults();
        }
        RawConfig raw = ConfigReader.parse(text, problems).orElse(null);
        if (raw == null) {
            return ConfigValues.defaults();
        }
        int version = ConfigReader.version(raw, problems);
        if (version > ConfigSchema.VERSION) {
            problems.warning(
                    null,
                    ConfigSchema.VERSION_KEY,
                    "this file is from a newer version of the bridge (" + version + "). This version reads version "
                            + ConfigSchema.VERSION + " and ignores settings it does not know.");
        }
        boolean upgrade = version < ConfigSchema.VERSION;
        if (upgrade) {
            migrations.stream()
                    .filter(migration -> migration.from() >= version && migration.from() < ConfigSchema.VERSION)
                    .forEach(migration -> migration.apply(raw));
        }
        ConfigValues values = ConfigReader.read(raw, problems);
        if (upgrade && !problems.hasErrors()) {
            saveUpgrade(file, original, values, version);
        }
        return values;
    }

    private static @Nullable String decode(byte[] bytes, Problems problems) {
        try {
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException invalid) {
            problems.error(null, "", "the file is not valid UTF-8. Save it with UTF-8 encoding.");
            return null;
        }
    }

    private static void createDefault(Path file, Problems problems) {
        try {
            AtomicFile.write(file, utf8(ConfigWriter.render(ConfigValues.defaults())));
            LOGGER.info("Created {} with default settings.", file);
        } catch (IOException failure) {
            problems.warning(
                    null,
                    "",
                    "could not create " + file + " (" + failure.getMessage()
                            + "). Using default settings; check that the folder is writable.");
        }
    }

    private void saveUpgrade(Path file, byte[] original, ConfigValues values, int fromVersion) {
        String upgraded = ConfigWriter.render(values);
        Path backup = directory.resolve(FILE + ".v" + fromVersion + ".bak");
        try {
            if (!readsBackAs(upgraded, values)) {
                throw new IOException("the upgraded file does not read back with the same values");
            }
            AtomicFile.write(backup, original);
            AtomicFile.write(file, utf8(upgraded));
            LOGGER.info(
                    "Upgraded {} from version {} to {}. The old file is saved as {}.",
                    file,
                    fromVersion,
                    ConfigSchema.VERSION,
                    backup);
        } catch (IOException failure) {
            keepUpgradedCopy(file, upgraded, failure);
        }
    }

    private void keepUpgradedCopy(Path file, String upgraded, IOException failure) {
        Path copy = directory.resolve(FILE + ".migrated");
        try {
            AtomicFile.write(copy, utf8(upgraded));
            LOGGER.warn(
                    "Could not upgrade {} ({}). It is unchanged and the bridge uses the upgraded settings for now."
                            + " Review {} and replace {} with it.",
                    file,
                    failure.getMessage(),
                    copy,
                    file);
        } catch (IOException copyFailure) {
            LOGGER.warn(
                    "Could not upgrade {} ({}) or write {} ({}). It is unchanged and the bridge uses the upgraded"
                            + " settings for now. Check that the folder is writable.",
                    file,
                    failure.getMessage(),
                    copy,
                    copyFailure.getMessage());
        }
    }

    static boolean readsBackAs(String text, ConfigValues expected) {
        Problems check = new Problems(FILE);
        RawConfig raw = ConfigReader.parse(text, check).orElse(null);
        if (raw == null) {
            return false;
        }
        int version = ConfigReader.version(raw, check);
        ConfigValues actual = ConfigReader.read(raw, check);
        return !check.hasErrors() && version == ConfigSchema.VERSION && actual.equals(expected);
    }

    private static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
