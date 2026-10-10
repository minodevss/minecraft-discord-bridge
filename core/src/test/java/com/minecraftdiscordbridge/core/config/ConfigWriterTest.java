package com.minecraftdiscordbridge.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.minecraftdiscordbridge.core.discord.DiscordId;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConfigWriterTest {
    @Test
    void rendersDefaultsExactlyAsTheGoldenFile() throws IOException {
        assertThat(ConfigWriter.render(ConfigValues.defaults())).isEqualTo(ConfigFiles.resource("default-config.yml"));
    }

    @Test
    void defaultFileReadsBackAsDefaults() {
        assertThat(readsBackAs(ConfigWriter.render(ConfigValues.defaults()), ConfigValues.defaults()))
                .isTrue();
    }

    @Test
    void rendersUnusualValuesSoTheyReadBackUnchanged() {
        ConfigValues values = ConfigValues.builder()
                .setDefaultsForAll()
                .set(ConfigSchema.FORMAT, "\"quoted\" \\ back\nnew line\ttab é 한글 😀 \u0007   # not a comment")
                .set(ConfigSchema.BLOCKED_WORDS, List.of("(?i)bad\\s+word", "[a-z]+: x"))
                .set(
                        ConfigSchema.ROLE_SYNC,
                        List.of(
                                new RoleSyncRule(
                                        new DiscordId(123456789012345678L),
                                        "vip: gold",
                                        SyncDirection.MINECRAFT_TO_DISCORD),
                                new RoleSyncRule(
                                        new DiscordId(223456789012345678L), "vip", SyncDirection.DISCORD_TO_MINECRAFT)))
                .set(ConfigSchema.INPUT_USERS, List.of("12345678901234567890"))
                .set(ConfigSchema.CHAT_CHANNEL, "123456789012345678")
                .build();

        assertThat(readsBackAs(ConfigWriter.render(values), values)).isTrue();
    }

    @Test
    void writesListsAsBlockItems() {
        ConfigValues values = ConfigValues.builder()
                .setDefaultsForAll()
                .set(ConfigSchema.INPUT_USERS, List.of("123456789012345678"))
                .set(
                        ConfigSchema.ROLE_SYNC,
                        List.of(
                                new RoleSyncRule(
                                        new DiscordId(223456789012345678L), "vip", SyncDirection.DISCORD_TO_MINECRAFT),
                                new RoleSyncRule(
                                        new DiscordId(323456789012345678L),
                                        "staff",
                                        SyncDirection.MINECRAFT_TO_DISCORD)))
                .build();

        assertThat(ConfigWriter.render(values))
                .contains("  input-users:\n    - \"123456789012345678\"\n")
                .contains("""
                        role-sync:
                          - discord-role: "223456789012345678"
                            group: "vip"
                          - discord-role: "323456789012345678"
                            group: "staff"
                            direction: "minecraft-to-discord"
                        """);
    }

    @Test
    void everyOptionSitsInTheSectionItsPathNames() {
        for (Section section : ConfigSchema.SECTIONS) {
            assertThat(section.options())
                    .allSatisfy(option -> assertThat(option.path()).isEqualTo(section.name() + "." + option.key()));
        }
        assertThat(ConfigSchema.LAYOUT)
                .filteredOn(block -> block instanceof Option<?>)
                .allSatisfy(option -> assertThat(((Option<?>) option).path()).doesNotContain("."));
    }

    private static boolean readsBackAs(String text, ConfigValues expected) {
        Problems check = new Problems(ConfigLoader.FILE);
        RawConfig raw = ConfigReader.parse(text, check).orElse(null);
        if (raw == null) {
            return false;
        }
        int version = ConfigReader.version(raw, check);
        ConfigValues actual = ConfigReader.read(raw, check);
        return !check.hasErrors() && version == ConfigSchema.VERSION && actual.equals(expected);
    }
}
