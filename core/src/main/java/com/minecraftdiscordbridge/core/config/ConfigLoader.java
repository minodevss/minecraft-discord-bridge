package com.minecraftdiscordbridge.core.config;

import static com.minecraftdiscordbridge.core.log.Log.LOGGER;

import com.minecraftdiscordbridge.core.storage.AtomicFile;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public final class ConfigLoader {
    static final String FILE = "config.yml";

    private final Path directory;
    private final Map<String, String> environment;

    public ConfigLoader(Path directory, Map<String, String> environment) {
        this.directory = directory;
        this.environment = Map.copyOf(environment);
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
                            + "). Check the file permissions");
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
                            + ConfigSchema.VERSION + " and ignores settings it does not know");
        }
        return ConfigReader.read(raw, problems);
    }

    private static @Nullable String decode(byte[] bytes, Problems problems) {
        try {
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException invalid) {
            problems.error(null, "", "the file is not valid UTF-8. Save it with UTF-8 encoding");
            return null;
        }
    }

    private static void createDefault(Path file, Problems problems) {
        try {
            AtomicFile.write(file, utf8(ConfigWriter.render(ConfigValues.defaults())));
            LOGGER.info("Created {} with default settings", file);
        } catch (IOException failure) {
            problems.warning(
                    null,
                    "",
                    "could not create " + file + " (" + failure.getMessage()
                            + "). Using default settings; check that the folder is writable");
        }
    }

    private static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
