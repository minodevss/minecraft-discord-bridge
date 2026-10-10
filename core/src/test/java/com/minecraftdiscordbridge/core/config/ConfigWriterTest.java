package com.minecraftdiscordbridge.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.minecraftdiscordbridge.core.discord.DiscordId;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ConfigWriterTest {
    @Test
    void rendersDefaultsExactlyAsTheGoldenFile() throws IOException {
        assertThat(ConfigWriter.render(ConfigValues.defaults())).isEqualTo(ConfigFiles.resource("default-config.yml"));
    }

    @Test
    void defaultFileReadsBackAsDefaults() {
        assertThat(ConfigLoader.readsBackAs(ConfigWriter.render(ConfigValues.defaults()), ConfigValues.defaults()))
                .isTrue();
    }

    @Test
    void rendersUnusualValuesSoTheyReadBackUnchanged() {
        ConfigValues values = ConfigValues.builder()
                .setDefaultsForAll()
                .set(ConfigSchema.FORMAT, "\"quoted\" \\ back\nnew line\ttab é 한글 😀 \u0007   # not a comment")
                .set(ConfigSchema.BLOCKED_WORDS, List.of("(?i)bad\\s+word", "[a-z]+: x"))
                .set(
                        ConfigSchema.ROLE_SYNC_RULES,
                        List.of(new RoleSyncRule(
                                new DiscordId(123456789012345678L), "vip: gold", SyncDirection.MINECRAFT_TO_DISCORD)))
                .set(ConfigSchema.INPUT_USERS, List.of("12345678901234567890"))
                .set(ConfigSchema.CHAT_CHANNEL, "123456789012345678")
                .build();

        assertThat(ConfigLoader.readsBackAs(ConfigWriter.render(values), values))
                .isTrue();
    }

    @Test
    void writesListsAsBlockItems() {
        ConfigValues values = ConfigValues.builder()
                .setDefaultsForAll()
                .set(ConfigSchema.INPUT_USERS, List.of("123456789012345678"))
                .set(
                        ConfigSchema.ROLE_SYNC_RULES,
                        List.of(new RoleSyncRule(
                                new DiscordId(223456789012345678L), "vip", SyncDirection.DISCORD_TO_MINECRAFT)))
                .build();

        assertThat(ConfigWriter.render(values))
                .contains("  input-users:\n    - \"123456789012345678\"\n")
                .contains("""
                          rules:
                            - discord-role: "223456789012345678"
                              group: "vip"
                              direction: "discord-to-minecraft"
                        """);
    }

    @Test
    void everyOptionHasACommentAndAKnownSection() {
        Set<String> sections = new HashSet<>(Set.of(""));
        ConfigSchema.SECTIONS.forEach(section -> sections.add(section.name()));
        for (Option<?> option : ConfigSchema.OPTIONS) {
            assertThat(option.section()).isIn(sections);
            assertThat(option.comment()).isNotEmpty();
        }
    }
}
