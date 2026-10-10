package com.minecraftdiscordbridge.core.config;

import static com.minecraftdiscordbridge.core.config.ConfigFiles.loaded;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigVersionTest {
    @TempDir
    Path directory;

    @Test
    void neverRewritesAFileThatIsAlreadyCurrent() throws IOException {
        String current = """
                # my own notes stay
                config-version: 1
                chat:
                  format: '<author>: <message>'
                  unknown-thing: 1
                """;
        Path file = write(current);
        FileTime before = FileTime.from(Instant.parse("2026-01-01T00:00:00Z"));
        Files.setLastModifiedTime(file, before);

        loaded(loader().load());

        assertThat(file).hasContent(current);
        assertThat(Files.getLastModifiedTime(file)).isEqualTo(before);
        try (var files = Files.list(directory)) {
            assertThat(files.map(path -> path.getFileName().toString()))
                    .containsExactlyInAnyOrder("config.yml", "secrets.yml");
        }
    }

    @Test
    void readsAFileFromANewerVersionWithoutTouchingIt() throws IOException {
        String newer = """
                config-version: 7
                chat:
                  format: "x"
                """;
        Path file = write(newer);

        ConfigResult result = loader().load();

        assertThat(loaded(result).config().chat().format()).isEqualTo("x");
        assertThat(ConfigFiles.warnings(result))
                .containsExactly("config.yml config-version: this file is from a newer version of the bridge (7). This"
                        + " version reads version 1 and ignores settings it does not know");
        assertThat(file).hasContent(newer);
    }

    private ConfigLoader loader() {
        return new ConfigLoader(directory, Map.of());
    }

    private Path write(String content) throws IOException {
        return Files.writeString(directory.resolve("config.yml"), content);
    }
}
