package com.minecraftdiscordbridge.core;

import static com.minecraftdiscordbridge.core.Harness.CHANNELS;
import static com.minecraftdiscordbridge.core.Harness.CHAT;
import static com.minecraftdiscordbridge.core.Harness.CONSOLE;
import static com.minecraftdiscordbridge.core.Harness.MEMBER;
import static com.minecraftdiscordbridge.core.Harness.OWNER;
import static com.minecraftdiscordbridge.core.Harness.ROLE;
import static com.minecraftdiscordbridge.core.Harness.STEVE;
import static org.assertj.core.api.Assertions.assertThat;

import com.minecraftdiscordbridge.core.access.GroupCheck;
import com.minecraftdiscordbridge.core.discord.DiscordMessage;
import com.minecraftdiscordbridge.core.discord.MemberLookup;
import com.minecraftdiscordbridge.core.discord.RoleInfo;
import com.minecraftdiscordbridge.core.platform.GameAction;
import com.minecraftdiscordbridge.core.platform.GameEvent;
import com.minecraftdiscordbridge.core.platform.JoinDecision;
import com.minecraftdiscordbridge.core.text.TextRenderer;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AccessTest {
    private static final String GATE = CHANNELS + """
            linking:
              require-for-join: true
            """;
    private static final String ROLE_GATE = CHANNELS + """
            linking:
              require-for-join: true
              required-role: "777777777777777777"
            """;

    @TempDir
    Path directory;

    @Test
    void keepsUnlinkedPlayersOutWithACodeThatLinksThem() throws Exception {
        try (Harness harness = Harness.started(directory, GATE)) {
            String screen = denied(join(harness));
            assertThat(screen).contains("/link ");

            RecordingReply linked = link(harness, Harness.code(screen));

            assertThat(linked.next().text()).startsWith("Linked");
            Waits.until("the link to be saved", () -> join(harness) instanceof JoinDecision.Allow);
        }
    }

    @Test
    void decidesWithTheUnreachablePolicyWhenDiscordDoesNotAnswerInTime() throws Exception {
        try (Harness harness = Harness.started(directory, ROLE_GATE)) {
            linkSteve(harness);
            harness.discord().members = userId -> new CompletableFuture<>();

            long started = System.nanoTime();
            JoinDecision decision = join(harness);

            assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofMillis(3800));
            assertThat(denied(decision)).startsWith("Your Discord account cannot be checked right now.");
        }
    }

    @Test
    void letsPlayersInWhenTheGateIsOff() throws Exception {
        try (Harness harness = Harness.started(directory, CHANNELS)) {
            assertThat(join(harness)).isInstanceOf(JoinDecision.Allow.class);
            assertThat(harness.discord().memberLookups).isEmpty();
        }
    }

    @Test
    void letsPlayersInWhileTheConfigIsBroken() throws Exception {
        try (Harness harness = Harness.create(directory, GATE + "events:\n  join: maybe\n")) {
            harness.bridge.start();
            harness.settledStatus();

            assertThat(join(harness)).isInstanceOf(JoinDecision.Allow.class);
        }
    }

    @Test
    void syncsTheGroupOnLinkJoinAndDiscordRoleChanges() throws Exception {
        try (Harness harness = Harness.create(directory, CHANNELS + """
                role-sync:
                  rules:
                    - discord-role: "777777777777777777"
                      group: "vip"
                      direction: "discord-to-minecraft"
                """)) {
            harness.groups.groups.put("vip", new GroupCheck.Safe());
            harness.bridge.start();
            RecordingDiscord discord = harness.discord();
            discord.roles.put(ROLE, new RoleInfo(ROLE, "VIP", Set.of(), true));
            discord.statusChanged();
            discord.members =
                    userId -> CompletableFuture.completedFuture(new MemberLookup.Member(userId, Set.of(ROLE)));
            String given = "+vip " + STEVE.id();

            linkSteve(harness);
            Waits.until("the group to be given on link", () -> harness.groups.changes.contains(given));
            harness.groups.members.remove(STEVE.id());
            harness.groups.changes.clear();

            harness.publish(new GameEvent.PlayerJoined(STEVE, false));
            Waits.until("the group to be given on join", () -> harness.groups.changes.contains(given));

            discord.memberUpdated(new MemberLookup.Member(MEMBER, Set.of()));
            Waits.until("the group to be taken", () -> harness.groups.changes.contains("-vip " + STEVE.id()));
        }
    }

    @Test
    void runsConsoleCommandsFromListedUsersInTheConsoleChannel() throws Exception {
        try (Harness harness = Harness.started(directory, CHANNELS + """
                  console-channel: "444444444444444444"
                console:
                  input-users: ["555555555555555555"]
                """)) {
            RecordingDiscord discord = harness.discord();
            assertThat(discord.settings.channelIds()).contains(CONSOLE);

            discord.receive(fromOwner("say hello"));

            Waits.until(
                    "the command to run",
                    () -> harness.platform.actions.contains(new GameAction.RunConsoleCommand("say hello")));
            assertThat(discord.nextSent().text()).isEqualTo("<@555555555555555555> ran a console command: say hello");
        }
    }

    private static JoinDecision join(Harness harness) {
        try {
            return harness.bridge.checkJoin(STEVE, false).get(5, TimeUnit.SECONDS);
        } catch (Exception failure) {
            throw new AssertionError(failure);
        }
    }

    private static void linkSteve(Harness harness) {
        String reply =
                harness.run(RecordingSource.player(STEVE.id(), "Steve"), "link").nextPlain();
        assertThat(link(harness, Harness.code(reply)).next().text()).startsWith("Linked");
        Waits.until("the link to be stored", () -> harness.status().contains("Linked accounts: 1"));
    }

    private static RecordingReply link(Harness harness, String code) {
        return harness.discord().command(Harness.request("link", null, Map.of("code", code), MEMBER, CHAT));
    }

    private static String denied(JoinDecision decision) {
        assertThat(decision).isInstanceOf(JoinDecision.Deny.class);
        return TextRenderer.plain(TextRenderer.fromJson(((JoinDecision.Deny) decision).json()));
    }

    private static DiscordMessage fromOwner(String content) {
        return new DiscordMessage(
                CONSOLE,
                1,
                new DiscordMessage.Sender(OWNER, "Owner", null, false, false),
                content,
                Map.of(),
                Map.of(),
                Map.of(),
                List.of(),
                null,
                List.of(),
                false,
                false,
                false);
    }
}
