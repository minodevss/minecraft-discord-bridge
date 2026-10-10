package com.minecraftdiscordbridge.core;

import static com.minecraftdiscordbridge.core.Harness.CHANNELS;
import static com.minecraftdiscordbridge.core.Harness.CHAT;
import static com.minecraftdiscordbridge.core.Harness.EVENTS;
import static com.minecraftdiscordbridge.core.Harness.STEVE;
import static com.minecraftdiscordbridge.core.Harness.TOKEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.minecraftdiscordbridge.core.discord.CommandSpec;
import com.minecraftdiscordbridge.core.discord.DiscordSettings;
import com.minecraftdiscordbridge.core.discord.OutboundMessage;
import com.minecraftdiscordbridge.core.fakes.RecordingLogs;
import com.minecraftdiscordbridge.core.fakes.RecordingSource;
import com.minecraftdiscordbridge.core.platform.GameCommand;
import com.minecraftdiscordbridge.core.platform.GameEvent;
import com.minecraftdiscordbridge.core.setup.SetupGuide;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.event.Level;

class StartupTest {
    @TempDir
    Path directory;

    @Test
    void connectsWithTheConfiguredChannelsAndCommands() throws IOException {
        try (Harness harness = Harness.started(directory, CHANNELS)) {
            DiscordSettings settings = harness.discord().settings;

            assertThat(settings.token()).isEqualTo(TOKEN);
            assertThat(settings.guild()).isEmpty();
            assertThat(settings.channelIds()).containsExactlyInAnyOrder(CHAT, EVENTS);
            assertThat(settings.requestMembersIntent()).isFalse();
            assertThat(commandNames(settings)).containsExactly("setup", "list", "link", "unlink");
            assertThat(harness.discord().started).isTrue();
        }
    }

    @Test
    void offersOnlySetupUntilAChannelIsChosen() throws IOException {
        try (Harness harness = Harness.started(directory, "config-version: 1\n")) {
            DiscordSettings settings = harness.discord().settings;

            assertThat(settings.channelIds()).isEmpty();
            assertThat(commandNames(settings)).containsExactly("setup");
        }
    }

    @Test
    void asksForTheMembersIntentOnlyWhenARoleIsRequired() throws IOException {
        try (Harness harness = Harness.started(directory, CHANNELS + """
                linking:
                  required-role: "777777777777777777"
                """)) {
            assertThat(harness.discord().settings.requestMembersIntent()).isTrue();
        }
    }

    @Test
    void needsNoMembersIntentToRequireOnlyALink() throws IOException {
        try (Harness harness = Harness.started(directory, CHANNELS + """
                linking:
                  require-for-join: true
                """)) {
            assertThat(harness.discord().settings.requestMembersIntent()).isFalse();
        }
    }

    @Test
    void staysIdleWithoutATokenAndKeepsTheServerUnaffected() throws IOException {
        try (Harness harness = Harness.create(directory, CHANNELS, Map.of())) {
            harness.bridge.start();

            assertThat(harness.settledStatus())
                    .startsWith("Discord: not connected")
                    .contains("No bot token is set");
            assertThatCode(() -> harness.publish(new GameEvent.PlayerChatted(STEVE, "hi")))
                    .doesNotThrowAnyException();
            assertThat(harness.discords).isEmpty();
        }
    }

    @Test
    void explainsTheMissingTokenInOneLineWithTheSetupGuide() throws IOException {
        try (RecordingLogs.Capture logs = RecordingLogs.capture();
                Harness harness = Harness.create(directory, CHANNELS, Map.of())) {
            harness.bridge.start();
            harness.settledStatus();

            assertThat(logs.lines(Level.WARN))
                    .singleElement()
                    .asString()
                    .contains(SetupGuide.QUICK_START)
                    .contains("https://discord.com/developers/applications")
                    .contains(directory.resolve("secrets.yml").toString())
                    .contains("/discord reload");
            assertThat(logs.lines()).noneSatisfy(line -> assertThat(line).contains("restart"));
        }
    }

    @Test
    void mirrorsTheConsoleOnlyWhileABotTokenIsSet() throws IOException {
        String mirrored = """
                config-version: 1
                chat:
                  channel: "222222222222222222"
                console:
                  channel: "444444444444444444"
                """;
        Files.writeString(directory.resolve("secrets.yml"), "discord-token: \"" + TOKEN + "\"\n");
        try (Harness harness = Harness.create(directory, mirrored, Map.of())) {
            harness.bridge.start();
            harness.discord();
            assertThat(consoleMirrorAttached()).isTrue();

            Files.writeString(directory.resolve("secrets.yml"), "discord-token: \"\"\n");
            assertThat(harness.run(RecordingSource.console(), GameCommand.RELOAD)
                            .nextPlain())
                    .contains("no bot token is set");

            assertThat(consoleMirrorAttached()).isFalse();
        }
    }

    @Test
    void hidesPendingLinkCodesInTheMirroredConsole() throws IOException {
        try (Harness harness = Harness.started(directory, CHANNELS + """
                console:
                  channel: "444444444444444444"
                """)) {
            String code = Harness.code(harness.run(RecordingSource.player(STEVE.id(), "Steve"), GameCommand.LINK)
                    .nextPlain());

            LogManager.getLogger("net.minecraft.server.MinecraftServer").error("Disconnecting Steve: {}", code);

            OutboundMessage mirrored = harness.discord().nextSent();
            assertThat(mirrored.channelId()).isEqualTo(Harness.CONSOLE);
            assertThat(mirrored.text())
                    .contains("Disconnecting Steve: [hidden]")
                    .doesNotContain(code);
        }
    }

    private static boolean consoleMirrorAttached() {
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        return context.getConfiguration().getRootLogger().getAppenders().containsKey("MinecraftDiscordBridgeConsole");
    }

    private static List<String> commandNames(DiscordSettings settings) {
        return settings.commands().stream().map(CommandSpec::name).toList();
    }
}
