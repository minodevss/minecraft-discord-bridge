package com.minecraftdiscordbridge.core.config;

import static com.minecraftdiscordbridge.core.config.ConfigFiles.errors;
import static com.minecraftdiscordbridge.core.config.ConfigFiles.loaded;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigLoaderTest {
    @TempDir
    Path directory;

    @Test
    void writesTheDefaultFilesOnFirstStart() throws IOException {
        Path dataDirectory = directory.resolve("plugins").resolve("MinecraftDiscordBridge");

        ConfigResult result = new ConfigLoader(dataDirectory, Map.of()).load();

        assertThat(loaded(result).config()).isEqualTo(BridgeConfig.defaults());
        assertThat(result.warnings()).isEmpty();
        assertThat(Files.readString(dataDirectory.resolve("config.yml")))
                .isEqualTo(ConfigFiles.resource("default-config.yml"));
        assertThat(dataDirectory.resolve("secrets.yml")).exists();
    }

    @Test
    void reloadReturnsErrorsSoTheCallerCanKeepTheOldSnapshot() throws IOException {
        ConfigLoader loader = new ConfigLoader(directory, Map.of());
        ConfigSnapshot first = loaded(loader.load());
        Path file = directory.resolve("config.yml");
        Files.writeString(file, Files.readString(file).replace("join: true", "join: maybe"));

        ConfigResult reload = loader.load();

        assertThat(errors(reload)).singleElement().asString().contains("events.join: expected true or false");
        assertThat(first.config()).isEqualTo(BridgeConfig.defaults());
    }

    @Test
    void reloadPicksUpChanges() throws IOException {
        ConfigLoader loader = new ConfigLoader(directory, Map.of());
        loaded(loader.load());
        Path file = directory.resolve("config.yml");
        Files.writeString(file, Files.readString(file).replace("join: true", "join: false"));

        assertThat(loaded(loader.load()).config().events().join()).isFalse();
    }
}
