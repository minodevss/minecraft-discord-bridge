package com.minecraftdiscordbridge.core.config;

import static com.minecraftdiscordbridge.core.config.ConfigFiles.errors;
import static com.minecraftdiscordbridge.core.config.ConfigFiles.load;
import static com.minecraftdiscordbridge.core.config.ConfigFiles.loaded;
import static com.minecraftdiscordbridge.core.config.ConfigFiles.warnings;
import static org.assertj.core.api.Assertions.assertThat;

import com.minecraftdiscordbridge.core.discord.DiscordId;
import com.minecraftdiscordbridge.core.i18n.Language;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigValidationTest {
    @TempDir
    Path directory;

    @Test
    void reportsWrongTypeWithFileLineColumnAndKey() throws IOException {
        ConfigResult result = load(directory, """
                config-version: 1
                chat:
                  format:
                    - "a"
                """);

        assertThat(errors(result)).containsExactly("config.yml:4:5 chat.format: expected text, got a list");
    }

    @Test
    void collectsEveryErrorInOnePass() throws IOException {
        ConfigResult result = load(directory, """
                config-version: 1
                language: fr
                chat:
                  channel: 12345
                  blocked-words: ["(unclosed"]
                events:
                  join: yes
                """);

        assertThat(errors(result))
                .containsExactly(
                        "config.yml:2:11 language: expected en or ko, got \"fr\"",
                        "config.yml:4:12 chat.channel: expected a Discord ID (17 to 20 digits) or \"\", got 12345",
                        "config.yml:5:19 chat.blocked-words[0]: invalid regular expression: Unclosed group near index 9",
                        "config.yml:7:9 events.join: expected true or false, got \"yes\"");
    }

    @Test
    void warnsAboutUnknownKeysWithTheClosestKnownKey() throws IOException {
        ConfigResult result = load(directory, """
                config-version: 1
                chat:
                  formatt: "x"
                  join: true
                totally-unknown: 1
                """);

        loaded(result);
        assertThat(warnings(result))
                .containsExactly(
                        "config.yml:3:3 chat.formatt: unknown key is ignored; did you mean chat.format?",
                        "config.yml:4:3 chat.join: unknown key is ignored; did you mean events.join?",
                        "config.yml:5:1 totally-unknown: unknown key is ignored");
    }

    @Test
    void rejectsDuplicateKeys() throws IOException {
        ConfigResult result = load(directory, """
                config-version: 1
                chat:
                  format: "a"
                  format: "b"
                """);

        assertThat(errors(result))
                .containsExactly(
                        "config.yml:4:3 chat.format: duplicate key; it is already set at line 3. Remove one of them.");
    }

    @Test
    void readsDiscordIdsAsTextWhetherQuotedOrNot() throws IOException {
        ConfigSnapshot snapshot = loaded(load(directory, """
                config-version: 1
                chat:
                  channel: 123456789012345678
                events:
                  channel: "12345678901234567890"
                  server-start-ping-role: 18446744073709551615
                console:
                  channel:
                  input-users: [123456789012345678, "223456789012345678"]
                """));

        BridgeConfig config = snapshot.config();
        assertThat(config.chat().channel()).contains(new DiscordId(123456789012345678L));
        assertThat(config.events().channel()).map(DiscordId::toString).contains("12345678901234567890");
        assertThat(config.console().channel()).isEmpty();
        assertThat(config.events().serverStartPingRole())
                .map(DiscordId::toString)
                .contains("18446744073709551615");
        assertThat(config.console().inputUsers())
                .containsExactly(new DiscordId(123456789012345678L), new DiscordId(223456789012345678L));
    }

    @Test
    void rejectsDiscordIdsThatAreNotSeventeenToTwentyDigits() throws IOException {
        ConfigResult result = load(directory, """
                config-version: 1
                chat:
                  channel: 1.5e17
                events:
                  channel: "my-channel"
                console:
                  channel: 99999999999999999999
                  input-users: ["123", ""]
                """);

        assertThat(errors(result))
                .containsExactly(
                        "config.yml:3:12 chat.channel: expected a Discord ID (17 to 20 digits) or \"\", got 1.5e17",
                        "config.yml:5:12 events.channel: expected a Discord ID (17 to 20 digits) or \"\","
                                + " got \"my-channel\"",
                        "config.yml:7:12 console.channel: expected a Discord ID (17 to 20 digits) or \"\","
                                + " got 99999999999999999999",
                        "config.yml:8:17 console.input-users[0]: expected a Discord ID (17 to 20 digits), got \"123\"",
                        "config.yml:8:24 console.input-users[1]: expected a Discord ID (17 to 20 digits), got \"\"");
    }

    @Test
    void pointsAtTheBrokenFieldOfARoleSyncRule() throws IOException {
        ConfigResult result = load(directory, """
                config-version: 1
                role-sync:
                  - discord-role: "123456789012345678"
                    group: "vip"
                    direction: "both"
                  - group: "admin"
                    direction: "minecraft-to-discord"
                    dicord-role: "123456789012345678"
                """);

        assertThat(errors(result))
                .containsExactly(
                        "config.yml:5:16 role-sync[0].direction: expected discord-to-minecraft or"
                                + " minecraft-to-discord, got \"both\"",
                        "config.yml:6:5 role-sync[1]: missing discord-role; each rule needs discord-role and group");
        assertThat(warnings(result))
                .containsExactly("config.yml:8:5 role-sync[1].dicord-role: unknown key is ignored;"
                        + " did you mean discord-role?");
    }

    @Test
    void syncsRolesFromDiscordWhenARuleLeavesOutTheDirection() throws IOException {
        ConfigSnapshot snapshot = loaded(load(directory, """
                config-version: 1
                role-sync:
                  - discord-role: "123456789012345678"
                    group: "vip"
                """));

        assertThat(snapshot.config().roleSync())
                .containsExactly(new RoleSyncRule(
                        new DiscordId(123456789012345678L), "vip", SyncDirection.DISCORD_TO_MINECRAFT));
    }

    @Test
    void reportsYamlSyntaxErrorsWithPosition() throws IOException {
        ConfigResult result = load(directory, """
                config-version: 1
                chat: format: x
                """);

        assertThat(errors(result))
                .containsExactly("config.yml:2:13: invalid YAML: mapping values are not allowed here");
        assertThat(warnings(result)).isEmpty();
    }

    @Test
    void rejectsASectionThatIsNotAMap() throws IOException {
        ConfigResult result = load(directory, """
                config-version: 1
                chat: "hello"
                """);

        assertThat(errors(result))
                .containsExactly("config.yml:2:7 chat: expected a section of settings, got \"hello\"");
    }

    @Test
    void rejectsFilesThatAreNotUtf8() throws IOException {
        Files.write(directory.resolve("config.yml"), new byte[] {'a', ':', ' ', (byte) 0xC3, (byte) 0x28});

        ConfigResult result = new ConfigLoader(directory, Map.of()).load();

        assertThat(errors(result))
                .containsExactly("config.yml: the file is not valid UTF-8. Save it with UTF-8 encoding.");
    }

    @Test
    void usesDefaultsForAnEmptyFileAndAsksForTheVersion() throws IOException {
        ConfigResult result = load(directory, "");

        assertThat(loaded(result).config()).isEqualTo(BridgeConfig.defaults());
        assertThat(warnings(result))
                .containsExactly("config.yml config-version: missing; assuming 1. Add \"config-version: 1\" at the end"
                        + " of the file.");
    }

    @Test
    void readsEverySettingIntoTheRecords() throws IOException {
        ConfigSnapshot snapshot = loaded(load(directory, """
                language: ko
                chat:
                  channel: "100000000000000001"
                  minecraft-to-discord: false
                  discord-to-minecraft: false
                  format: "<author> » <message>"
                  avatar-url: "https://example.com/{texture}"
                  ping-linked-players: true
                  blocked-words: ["(?i)bad"]
                events:
                  channel: "100000000000000002"
                  join: false
                  leave: false
                  death: false
                  advancement: false
                  server-start: false
                  server-stop: false
                  server-start-ping-role: "100000000000000004"
                player-count:
                  bot-status: false
                  channel-topic: true
                linking:
                  enabled: false
                  require-for-join: true
                  required-role: "100000000000000005"
                  when-discord-unreachable: allow
                console:
                  channel: "100000000000000003"
                  input-users: ["100000000000000007"]
                discord-commands:
                  whitelist: true
                  kick: true
                role-sync:
                  - discord-role: "100000000000000006"
                    group: "vip"
                    direction: minecraft-to-discord
                config-version: 1
                """));

        assertThat(snapshot.config())
                .isEqualTo(new BridgeConfig(
                        Language.KO,
                        new ChatConfig(
                                Optional.of(new DiscordId(100000000000000001L)),
                                false,
                                false,
                                "<author> » <message>",
                                "https://example.com/{texture}",
                                true,
                                List.of(java.util.regex.Pattern.compile("(?i)bad"))),
                        new EventsConfig(
                                Optional.of(new DiscordId(100000000000000002L)),
                                false,
                                false,
                                false,
                                false,
                                false,
                                false,
                                Optional.of(new DiscordId(100000000000000004L))),
                        new PlayerCountConfig(false, true),
                        new LinkingConfig(
                                false, true, Optional.of(new DiscordId(100000000000000005L)), UnreachablePolicy.ALLOW),
                        new ConsoleConfig(
                                Optional.of(new DiscordId(100000000000000003L)),
                                List.of(new DiscordId(100000000000000007L))),
                        new DiscordCommandsConfig(true, true),
                        List.of(new RoleSyncRule(
                                new DiscordId(100000000000000006L), "vip", SyncDirection.MINECRAFT_TO_DISCORD))));
        assertThat(snapshot.config().chat().blockedWords().get(0).matcher("BAD").find())
                .isTrue();
    }
}
