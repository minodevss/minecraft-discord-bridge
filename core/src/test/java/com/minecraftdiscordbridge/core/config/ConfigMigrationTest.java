package com.minecraftdiscordbridge.core.config;

import static com.minecraftdiscordbridge.core.config.ConfigFiles.errors;
import static com.minecraftdiscordbridge.core.config.ConfigFiles.loaded;
import static org.assertj.core.api.Assertions.assertThat;

import com.minecraftdiscordbridge.core.config.YamlValue.Scalar;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigMigrationTest {
    private static final String VERSION_ZERO = """
            # my own notes
            config-version: 0
            discord:
              channel: 123456789012345678
            relay:
              to-discord: "no"
            legacy:
              colors: true
            """;
    private static final Migration ZERO_TO_ONE = new Migration(
            0,
            List.of(
                    MigrationStep.rename("discord.channel", "chat-channel"),
                    MigrationStep.move("relay.to-discord", "chat.minecraft-to-discord"),
                    MigrationStep.mapValue(
                            "chat.minecraft-to-discord",
                            value -> new Scalar(
                                    String.valueOf(!"no".equals(((Scalar) value).text())),
                                    Scalar.Kind.BOOLEAN,
                                    value.at())),
                    MigrationStep.delete("legacy")));

    @TempDir
    Path directory;

    @Test
    void upgradesAnOldFileAndKeepsABackup() throws IOException {
        Path file = write(VERSION_ZERO);

        ConfigSnapshot snapshot = loaded(loader().load());

        assertThat(snapshot.config().discord().chatChannel()).contains("123456789012345678");
        assertThat(snapshot.config().chat().minecraftToDiscord()).isFalse();
        assertThat(directory.resolve("config.yml.v0.bak")).hasContent(VERSION_ZERO);
        String upgraded = Files.readString(file);
        assertThat(upgraded)
                .contains("config-version: 1\n")
                .contains("  chat-channel: \"123456789012345678\"\n")
                .contains("  minecraft-to-discord: false\n")
                .contains("# Channel for chat.")
                .doesNotContain("legacy")
                .doesNotContain("my own notes");
        assertThat(directory.resolve("config.yml.migrated")).doesNotExist();
        assertThat(loaded(loader().load())).isEqualTo(snapshot);
    }

    @Test
    void keepsTheOriginalAndWritesAMigratedCopyWhenTheUpgradeCannotBeSaved() throws IOException {
        Path file = write(VERSION_ZERO);
        Path blockedBackup = Files.createDirectories(directory.resolve("config.yml.v0.bak"));
        Files.writeString(blockedBackup.resolve("keep"), "x");

        ConfigSnapshot snapshot = loaded(loader().load());

        assertThat(file).hasContent(VERSION_ZERO);
        assertThat(snapshot.config().discord().chatChannel()).contains("123456789012345678");
        Path migrated = directory.resolve("config.yml.migrated");
        assertThat(Files.readString(migrated))
                .contains("config-version: 1\n")
                .contains("  chat-channel: \"123456789012345678\"\n");
        assertThat(ConfigLoader.readsBackAs(Files.readString(migrated), valuesOf(snapshot)))
                .isTrue();
    }

    @Test
    void leavesTheFileAloneWhenTheUpgradedSettingsAreInvalid() throws IOException {
        String broken = VERSION_ZERO.replace("123456789012345678", "not-an-id");
        Path file = write(broken);

        List<String> errors = errors(loader().load());

        assertThat(errors)
                .containsExactly("config.yml:4:12 discord.chat-channel: expected a Discord ID (17 to 20"
                        + " digits) or \"\", got \"not-an-id\"");
        assertThat(file).hasContent(broken);
        assertThat(directory.resolve("config.yml.v0.bak")).doesNotExist();
        assertThat(directory.resolve("config.yml.migrated")).doesNotExist();
    }

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
                        + " version reads version 1 and ignores settings it does not know.");
        assertThat(file).hasContent(newer);
    }

    @Test
    void moveCarriesNestedKeysAlong() {
        RawConfig raw =
                RawConfig.parse("""
                old:
                  a: 1
                  b: 2
                """, Set.of(), new Problems("config.yml")).orElseThrow();

        MigrationStep.move("old", "fresh").apply(raw);

        assertThat(raw.paths()).containsExactly("fresh.a", "fresh.b");
    }

    private ConfigLoader loader() {
        return new ConfigLoader(directory, Map.of(), List.of(ZERO_TO_ONE));
    }

    private Path write(String content) throws IOException {
        return Files.writeString(directory.resolve("config.yml"), content);
    }

    private static ConfigValues valuesOf(ConfigSnapshot snapshot) {
        BridgeConfig config = snapshot.config();
        return ConfigValues.builder()
                .setDefaultsForAll()
                .set(ConfigSchema.CHAT_CHANNEL, config.discord().chatChannel().orElse(""))
                .set(ConfigSchema.MINECRAFT_TO_DISCORD, config.chat().minecraftToDiscord())
                .build();
    }
}
