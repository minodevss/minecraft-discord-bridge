package com.minecraftdiscordbridge.core;

import com.minecraftdiscordbridge.core.codes.Codes;
import com.minecraftdiscordbridge.core.discord.CommandRequest;
import com.minecraftdiscordbridge.core.discord.DiscordId;
import com.minecraftdiscordbridge.core.discord.DiscordMessage;
import com.minecraftdiscordbridge.core.discord.OfflineDiscord;
import com.minecraftdiscordbridge.core.fakes.FakeGroups;
import com.minecraftdiscordbridge.core.fakes.FakePlatform;
import com.minecraftdiscordbridge.core.fakes.MutableClock;
import com.minecraftdiscordbridge.core.fakes.RecordingDiscord;
import com.minecraftdiscordbridge.core.fakes.RecordingSource;
import com.minecraftdiscordbridge.core.fakes.Waits;
import com.minecraftdiscordbridge.core.platform.GameEvent;
import com.minecraftdiscordbridge.core.platform.PlayerProfile;
import com.minecraftdiscordbridge.core.vanilla.FakeFetcher;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

final class Harness implements AutoCloseable {
    static final DiscordId GUILD = new DiscordId(111111111111111111L);
    static final DiscordId CHAT = new DiscordId(222222222222222222L);
    static final DiscordId EVENTS = new DiscordId(333333333333333333L);
    static final DiscordId CONSOLE = new DiscordId(444444444444444444L);
    static final DiscordId OWNER = new DiscordId(555555555555555555L);
    static final DiscordId MEMBER = new DiscordId(666666666666666666L);
    static final DiscordId ROLE = new DiscordId(777777777777777777L);
    static final String TOKEN = "test-token";
    static final String CHANNELS = """
            config-version: 1
            discord:
              chat-channel: "222222222222222222"
              events-channel: "333333333333333333"
            """;
    static final PlayerProfile STEVE =
            new PlayerProfile(UUID.fromString("00000000-0000-4000-8000-000000000001"), "Steve", "Steve", "abc", false);
    static final PlayerProfile ALEX =
            new PlayerProfile(UUID.fromString("00000000-0000-4000-8000-000000000002"), "Alex_2", "Alex", null, false);
    private static final Pattern CODE = Pattern.compile("[" + Codes.ALPHABET + "]{" + Codes.LENGTH + "}");

    final Path directory;
    final FakePlatform platform;
    final MutableClock clock = new MutableClock(Instant.parse("2026-10-10T00:00:00Z"));
    final List<RecordingDiscord> discords = new CopyOnWriteArrayList<>();
    final FakeFetcher mojang = new FakeFetcher();
    final FakeGroups groups = new FakeGroups();
    final Bridge bridge;

    private Harness(Path directory, Map<String, String> environment, Duration actionTimeout) {
        this.directory = directory;
        this.platform = new FakePlatform(directory);
        this.bridge = new Bridge(
                platform,
                (settings, loop, listener, webhooks, hashes) -> {
                    RecordingDiscord discord = new RecordingDiscord(settings, loop, listener);
                    discords.add(discord);
                    return discord;
                },
                environment,
                clock,
                new Codes(new Random(7)),
                actionTimeout,
                mojang,
                groups);
    }

    static Harness create(Path directory, String config) throws IOException {
        return create(directory, config, Map.of("MCDB_DISCORD_TOKEN", TOKEN));
    }

    static Harness create(Path directory, String config, Map<String, String> environment) throws IOException {
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("config.yml"), config, StandardCharsets.UTF_8);
        return new Harness(directory, environment, Duration.ofMillis(300));
    }

    static Harness started(Path directory, String config) throws IOException {
        Harness harness = create(directory, config);
        harness.bridge.start();
        harness.discord();
        return harness;
    }

    RecordingDiscord discord() {
        Waits.until("a Discord connection", () -> !discords.isEmpty());
        return discords.get(discords.size() - 1);
    }

    RecordingDiscord discord(int count) {
        Waits.until(count + " Discord connections", () -> discords.size() >= count);
        return discords.get(count - 1);
    }

    void publish(GameEvent event) {
        bridge.publish(event);
    }

    RecordingSource run(RecordingSource source, String... arguments) {
        bridge.command(source, Arrays.asList(arguments));
        return source;
    }

    String settledStatus() {
        Waits.until("the bridge to finish starting", () -> !status().contains(OfflineDiscord.STARTING));
        return status();
    }

    String status() {
        RecordingSource console = run(RecordingSource.console(), "status");
        List<String> lines = new ArrayList<>();
        String line;
        do {
            line = console.nextPlain();
            lines.add(line);
        } while (!line.startsWith("Game events dropped"));
        return String.join("\n", lines);
    }

    void writeConfig(String config) throws IOException {
        Files.writeString(directory.resolve("config.yml"), config, StandardCharsets.UTF_8);
    }

    static CommandRequest request(
            String command,
            @Nullable String subcommand,
            Map<String, String> options,
            DiscordId user,
            DiscordId channel) {
        return new CommandRequest(
                command, Optional.ofNullable(subcommand), options, GUILD, channel, user, "Alex", false, "en-US");
    }

    static CommandRequest managerRequest(
            String command, Map<String, String> options, DiscordId user, DiscordId channel) {
        return new CommandRequest(command, Optional.empty(), options, GUILD, channel, user, "Alex", true, "en-US");
    }

    static DiscordMessage message(DiscordId channel, String content) {
        return new DiscordMessage(
                channel,
                new DiscordMessage.Sender(MEMBER, "Alex", OptionalInt.empty(), false, false),
                content,
                Map.of(),
                Map.of(),
                Map.of(),
                List.of(),
                Optional.empty(),
                List.of(),
                false,
                false,
                false);
    }

    static String code(String reply) {
        Matcher matcher = CODE.matcher(reply);
        if (!matcher.find()) {
            throw new AssertionError("No code in: " + reply);
        }
        return matcher.group();
    }

    @Override
    public void close() {
        bridge.stop();
    }
}
