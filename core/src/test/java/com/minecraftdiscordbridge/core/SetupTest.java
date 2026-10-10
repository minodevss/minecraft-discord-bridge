package com.minecraftdiscordbridge.core;

import static com.minecraftdiscordbridge.core.Harness.CHAT;
import static com.minecraftdiscordbridge.core.Harness.CONSOLE;
import static com.minecraftdiscordbridge.core.Harness.EVENTS;
import static com.minecraftdiscordbridge.core.Harness.GUILD;
import static com.minecraftdiscordbridge.core.Harness.MEMBER;
import static com.minecraftdiscordbridge.core.Harness.OWNER;
import static org.assertj.core.api.Assertions.assertThat;

import com.minecraftdiscordbridge.core.discord.CommandSpec;
import com.minecraftdiscordbridge.core.discord.DiscordId;
import com.minecraftdiscordbridge.core.fakes.RecordingDiscord;
import com.minecraftdiscordbridge.core.fakes.RecordingReply;
import com.minecraftdiscordbridge.core.fakes.RecordingSource;
import com.minecraftdiscordbridge.core.platform.GameCommand;
import com.minecraftdiscordbridge.core.storage.BindingStore;
import com.minecraftdiscordbridge.core.storage.ChannelRole;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SetupTest {
    private static final String UNBOUND = "config-version: 1\n";

    @TempDir
    Path directory;

    @Test
    void letsTheOwnerChooseTheChatChannelAndReconnectsThere() throws IOException {
        try (Harness harness = Harness.started(directory, UNBOUND)) {
            RecordingDiscord waiting = harness.discord();
            waiting.owners = Set.of(OWNER);

            RecordingReply reply = waiting.command(Harness.request("setup", null, Map.of(), OWNER, CHAT));

            assertThat(reply.next())
                    .isEqualTo(new RecordingReply.Answer("This channel now shows the Minecraft chat.", true));
            assertThat(BindingStore.open(directory).current().orElseThrow().channel(ChannelRole.CHAT))
                    .contains(CHAT);
            RecordingDiscord bound = harness.discord(2);
            assertThat(bound.settings.guild()).contains(GUILD);
            assertThat(bound.settings.channelIds()).containsExactly(CHAT);
            assertThat(bound.settings.commands()).extracting(CommandSpec::name).contains("list", "link");
            assertThat(waiting.shutDown).isTrue();
        }
    }

    @Test
    void offersSetupToMembersWhoCanManageTheServer() throws IOException {
        try (Harness harness = Harness.started(directory, UNBOUND)) {
            assertThat(harness.discord().settings.commands())
                    .singleElement()
                    .extracting(CommandSpec::access)
                    .isEqualTo(CommandSpec.Access.MANAGE_SERVER);
        }
    }

    @Test
    void offersChatAndEventsAsFixedChoices() throws IOException {
        try (Harness harness = Harness.started(directory, UNBOUND)) {
            CommandSpec.Option as =
                    harness.discord().settings.commands().get(0).options().get(0);

            assertThat(as.name()).isEqualTo("as");
            assertThat(as.autocomplete()).isFalse();
            assertThat(as.choices()).extracting(CommandSpec.Choice::value).containsExactly("chat", "events");
            assertThat(as.choices())
                    .allSatisfy(choice -> assertThat(choice.koreanName()).isPresent());
        }
    }

    @Test
    void addsAnEventsChannelToTheExistingChoice() throws IOException {
        try (Harness harness = Harness.started(directory, UNBOUND)) {
            harness.discord().owners = Set.of(OWNER);
            harness.discord()
                    .command(Harness.request("setup", null, Map.of(), OWNER, CHAT))
                    .next();
            RecordingDiscord bound = harness.discord(2);
            bound.owners = Set.of(OWNER);

            bound.command(Harness.request("setup", null, Map.of("as", "events"), OWNER, EVENTS))
                    .next();

            assertThat(harness.discord(3).settings.channelIds()).containsExactlyInAnyOrder(CHAT, EVENTS);
        }
    }

    @Test
    void explainsTheConsoleCodeToEveryoneElse() throws IOException {
        try (Harness harness = Harness.started(directory, UNBOUND)) {
            RecordingReply reply = harness.discord().command(Harness.request("setup", null, Map.of(), MEMBER, CHAT));

            RecordingReply.Answer answer = reply.next();
            assertThat(answer.ephemeral()).isTrue();
            assertThat(answer.text()).contains("Only the bot owner").contains("/discord setup");
            assertThat(BindingStore.open(directory).current()).isEmpty();
        }
    }

    @Test
    void acceptsAOneTimeConsoleCodeFromAServerManager() throws IOException {
        try (Harness harness = Harness.started(directory, UNBOUND)) {
            String code = Harness.code(
                    harness.run(RecordingSource.console(), GameCommand.SETUP).nextPlain());
            RecordingDiscord waiting = harness.discord();

            RecordingReply first = waiting.command(
                    Harness.managerRequest("setup", Map.of("code", code.toLowerCase(Locale.ROOT)), MEMBER, CHAT));
            assertThat(first.next().text()).isEqualTo("This channel now shows the Minecraft chat.");

            RecordingReply again =
                    waiting.command(Harness.managerRequest("setup", Map.of("code", code), MEMBER, EVENTS));
            assertThat(again.next().text()).contains("wrong or expired");
        }
    }

    @Test
    void acceptsTheSetupCodeAfterManyWrongLinkCodes() throws IOException {
        try (Harness harness = Harness.started(directory, UNBOUND)) {
            RecordingDiscord waiting = harness.discord();
            for (int i = 0; i < 50; i++) {
                waiting.command(Harness.request(
                                "link", null, Map.of("code", "WRONG"), new DiscordId(MEMBER.value() + i), CHAT))
                        .next();
            }
            String code = Harness.code(
                    harness.run(RecordingSource.console(), GameCommand.SETUP).nextPlain());

            RecordingReply reply = waiting.command(Harness.managerRequest("setup", Map.of("code", code), MEMBER, CHAT));

            assertThat(reply.next().text()).isEqualTo("This channel now shows the Minecraft chat.");
        }
    }

    @Test
    void refusesCodesFromMembersWhoCannotManageTheServer() throws IOException {
        try (Harness harness = Harness.started(directory, UNBOUND)) {
            String code = Harness.code(
                    harness.run(RecordingSource.console(), GameCommand.SETUP).nextPlain());

            RecordingReply reply =
                    harness.discord().command(Harness.request("setup", null, Map.of("code", code), MEMBER, CHAT));

            assertThat(reply.next().text()).contains("Manage Server");
            assertThat(BindingStore.open(directory).current()).isEmpty();
        }
    }

    @Test
    void expiresConsoleCodesAfterTenMinutes() throws IOException {
        try (Harness harness = Harness.started(directory, UNBOUND)) {
            String code = Harness.code(
                    harness.run(RecordingSource.console(), GameCommand.SETUP).nextPlain());
            harness.clock.advance(Duration.ofMinutes(10));

            RecordingReply reply =
                    harness.discord().command(Harness.managerRequest("setup", Map.of("code", code), MEMBER, CHAT));

            assertThat(reply.next().text()).contains("wrong or expired");
        }
    }

    @Test
    void neverBindsTheConsoleChannel() throws IOException {
        try (Harness harness = Harness.started(directory, """
                config-version: 1
                console:
                  channel: "444444444444444444"
                """)) {
            harness.discord().owners = Set.of(OWNER);

            RecordingReply reply = harness.discord().command(Harness.request("setup", null, Map.of(), OWNER, CONSOLE));

            assertThat(reply.next().text()).contains("console channel");
            assertThat(BindingStore.open(directory).current()).isEmpty();
        }
    }

    @Test
    void mentionsThatConfigYmlWinsOverSetup() throws IOException {
        try (Harness harness = Harness.started(directory, Harness.CHANNELS)) {
            harness.discord().owners = Set.of(OWNER);

            RecordingReply reply = harness.discord().command(Harness.request("setup", null, Map.of(), OWNER, CONSOLE));

            assertThat(reply.next().text())
                    .startsWith("This channel now shows the Minecraft chat.")
                    .contains("chat.channel in config.yml takes priority");
        }
    }
}
