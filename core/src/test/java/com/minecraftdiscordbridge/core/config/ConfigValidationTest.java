package com.minecraftdiscordbridge.core.config;

import static com.minecraftdiscordbridge.core.config.ConfigFiles.errors;
import static com.minecraftdiscordbridge.core.config.ConfigFiles.load;
import static com.minecraftdiscordbridge.core.config.ConfigFiles.loaded;
import static com.minecraftdiscordbridge.core.config.ConfigFiles.warnings;
import static org.assertj.core.api.Assertions.assertThat;

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
                discord:
                  chat-channel: 12345
                events:
                  join: yes
                chat:
                  blocked-words: ["(unclosed"]
                """);

        assertThat(errors(result))
                .containsExactly(
                        "config.yml:2:11 language: expected en or ko, got \"fr\"",
                        "config.yml:4:17 discord.chat-channel: expected a Discord ID (17 to 20 digits) or \"\", got 12345",
                        "config.yml:8:19 chat.blocked-words[0]: invalid regular expression: Unclosed group near index 9",
                        "config.yml:6:9 events.join: expected true or false, got \"yes\"");
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
                discord:
                  chat-channel: 123456789012345678
                  events-channel: "12345678901234567890"
                  console-channel:
                events:
                  server-start-ping-role: 99999999999999999999
                console:
                  input-users: [123456789012345678, "223456789012345678"]
                """));

        BridgeConfig config = snapshot.config();
        assertThat(config.discord().chatChannel()).contains("123456789012345678");
        assertThat(config.discord().eventsChannel()).contains("12345678901234567890");
        assertThat(config.discord().consoleChannel()).isEmpty();
        assertThat(config.events().serverStartPingRole()).contains("99999999999999999999");
        assertThat(config.console().inputUsers()).containsExactly("123456789012345678", "223456789012345678");
    }

    @Test
    void rejectsDiscordIdsThatAreNotSeventeenToTwentyDigits() throws IOException {
        ConfigResult result = load(directory, """
                config-version: 1
                discord:
                  chat-channel: 1.5e17
                  events-channel: "my-channel"
                console:
                  input-users: ["123", ""]
                """);

        assertThat(errors(result))
                .containsExactly(
                        "config.yml:3:17 discord.chat-channel: expected a Discord ID (17 to 20 digits) or \"\", got 1.5e17",
                        "config.yml:4:19 discord.events-channel: expected a Discord ID (17 to 20 digits) or \"\","
                                + " got \"my-channel\"",
                        "config.yml:6:17 console.input-users[0]: expected a Discord ID (17 to 20 digits), got \"123\"",
                        "config.yml:6:24 console.input-users[1]: expected a Discord ID (17 to 20 digits), got \"\"");
    }

    @Test
    void pointsAtTheBrokenFieldOfARoleSyncRule() throws IOException {
        ConfigResult result = load(directory, """
                config-version: 1
                role-sync:
                  rules:
                    - discord-role: "123456789012345678"
                      group: "vip"
                      direction: "both"
                    - group: "admin"
                      direction: "minecraft-to-discord"
                      dicord-role: "123456789012345678"
                """);

        assertThat(errors(result))
                .containsExactly(
                        "config.yml:6:18 role-sync.rules[0].direction: expected discord-to-minecraft or"
                                + " minecraft-to-discord, got \"both\"",
                        "config.yml:7:7 role-sync.rules[1]: missing discord-role; each rule needs discord-role, group"
                                + " and direction");
        assertThat(warnings(result))
                .containsExactly("config.yml:9:7 role-sync.rules[1].dicord-role: unknown key is ignored;"
                        + " did you mean discord-role?");
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
                .containsExactly("config.yml config-version: missing; assuming 1. Add \"config-version: 1\" at the top"
                        + " of the file.");
    }

    @Test
    void readsEverySettingIntoTheRecords() throws IOException {
        ConfigSnapshot snapshot = loaded(load(directory, """
                config-version: 1
                language: ko
                discord:
                  chat-channel: "100000000000000001"
                  events-channel: "100000000000000002"
                  console-channel: "100000000000000003"
                  auto-enable-message-content: false
                chat:
                  minecraft-to-discord: false
                  discord-to-minecraft: false
                  format: "<author> » <message>"
                  avatar-url: "https://example.com/{texture}"
                  avatar-url-fallback: "https://example.com/{uuid}"
                  ping-linked-players: true
                  blocked-words: ["(?i)bad"]
                events:
                  join: false
                  leave: false
                  death: false
                  advancement: false
                  server-start: false
                  server-stop: false
                  server-start-ping-role: "100000000000000004"
                status:
                  presence: false
                  topic: true
                linking:
                  enabled: false
                  require-for-join: true
                  required-role: "100000000000000005"
                  when-discord-unreachable: allow
                role-sync:
                  rules:
                    - discord-role: "100000000000000006"
                      group: "vip"
                      direction: minecraft-to-discord
                actions:
                  whitelist: true
                  kick: true
                console:
                  mirror: true
                  input-users: ["100000000000000007"]
                """));

        assertThat(snapshot.config())
                .isEqualTo(new BridgeConfig(
                        Language.KO,
                        new DiscordConfig(
                                Optional.of("100000000000000001"),
                                Optional.of("100000000000000002"),
                                Optional.of("100000000000000003"),
                                false),
                        new ChatConfig(
                                false,
                                false,
                                "<author> » <message>",
                                "https://example.com/{texture}",
                                "https://example.com/{uuid}",
                                true,
                                List.of(java.util.regex.Pattern.compile("(?i)bad"))),
                        new EventsConfig(false, false, false, false, false, false, Optional.of("100000000000000004")),
                        new StatusConfig(false, true),
                        new LinkingConfig(false, true, Optional.of("100000000000000005"), UnreachablePolicy.ALLOW),
                        new RoleSyncConfig(List.of(
                                new RoleSyncRule("100000000000000006", "vip", SyncDirection.MINECRAFT_TO_DISCORD))),
                        new ActionsConfig(true, true),
                        new ConsoleConfig(true, List.of("100000000000000007"))));
        assertThat(snapshot.config().chat().blockedWords().get(0).matcher("BAD").find())
                .isTrue();
    }
}
