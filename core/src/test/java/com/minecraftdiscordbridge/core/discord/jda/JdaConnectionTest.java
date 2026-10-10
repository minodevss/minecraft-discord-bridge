package com.minecraftdiscordbridge.core.discord.jda;

import static com.minecraftdiscordbridge.core.discord.jda.DiscordPayloads.APPLICATION_ID;
import static com.minecraftdiscordbridge.core.discord.jda.DiscordPayloads.CHANNEL_ID;
import static com.minecraftdiscordbridge.core.discord.jda.DiscordPayloads.GUILD_ID;
import static com.minecraftdiscordbridge.core.discord.jda.DiscordPayloads.MESSAGE_CONTENT_INTENT;
import static com.minecraftdiscordbridge.core.discord.jda.DiscordPayloads.OTHER_GUILD_ID;
import static com.minecraftdiscordbridge.core.discord.jda.DiscordPayloads.PLAYER_ID;
import static com.minecraftdiscordbridge.core.fakes.Waits.take;
import static com.minecraftdiscordbridge.core.fakes.Waits.until;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.minecraftdiscordbridge.core.discord.DiscordId;
import com.minecraftdiscordbridge.core.discord.DiscordStatus.State;
import com.minecraftdiscordbridge.core.discord.OutboundMessage;
import com.minecraftdiscordbridge.core.loop.Deadline;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import mockwebserver3.RecordedRequest;
import net.dv8tion.jda.api.utils.data.DataArray;
import org.junit.jupiter.api.Test;

class JdaConnectionTest extends JdaTestBase {
    @Test
    void becomesReadyAfterTheGatewaySaysReady() {
        bridge = start(TestSupport.settings(List.of()));

        awaitState(State.READY);
        assertThat(bridge.status().reason()).isEmpty();
        assertThat(take(listener.statuses).state()).isEqualTo(State.READY);
    }

    @Test
    void stopsWithAReasonInsteadOfThrowingWhenTheTokenIsInvalid() {
        discord.tokenValid = false;

        assertThatCode(() -> bridge = start(TestSupport.settings(List.of()))).doesNotThrowAnyException();

        awaitState(State.STOPPED);
        assertThat(bridge.status().reason().orElseThrow())
                .contains("rejected the bot token")
                .contains("secrets.yml");
        assertThat(discord.timeline).containsExactly("GET applications/@me");
    }

    @Test
    void turnsOnMessageContentBeforeLoggingInAndKeepsTheOtherFlags() {
        long otherFlags = (1L << 12) | (1L << 23) | ApplicationInfo.GATEWAY_GUILD_MEMBERS_LIMITED;
        discord.applicationFlags = otherFlags;

        bridge = start(TestSupport.settings(List.of()));

        awaitState(State.READY);
        RecordedRequest patch = discord.requests("PATCH", "applications/@me").get(0);
        assertThat(DiscordPayloads.body(patch).getLong("flags"))
                .isEqualTo(otherFlags | ApplicationInfo.GATEWAY_MESSAGE_CONTENT_LIMITED);
        assertThat(discord.timeline.indexOf("PATCH applications/@me"))
                .isLessThan(discord.timeline.indexOf("GET users/@me"))
                .isLessThan(discord.timeline.indexOf("GATEWAY connect"));
        assertThat(discord.identifies.get(0).getInt("intents") & MESSAGE_CONTENT_INTENT)
                .isNotZero();
    }

    @Test
    void degradesWhenDiscordRefusesToTurnOnMessageContent() {
        discord.applicationFlags = 0;
        discord.patchAllowed = false;

        bridge = start(TestSupport.settings(List.of()));

        awaitState(State.DEGRADED);
        assertThat(discord.requests("PATCH", "applications/@me")).hasSize(1);
        assertThat(discord.identifies.get(0).getInt("intents") & MESSAGE_CONTENT_INTENT)
                .isZero();
    }

    @Test
    void reconnectsOnceWithoutMessageContentWhenDiscordRefusesTheIntent() {
        discord.refuseMessageContent = true;

        bridge = start(TestSupport.settings(List.of()));

        awaitState(State.DEGRADED);
        assertThat(discord.identifies).hasSize(2);
        assertThat(discord.identifies.get(0).getInt("intents") & MESSAGE_CONTENT_INTENT)
                .isNotZero();
        assertThat(discord.identifies.get(1).getInt("intents") & MESSAGE_CONTENT_INTENT)
                .isZero();
        assertThat(bridge.status().reason().orElseThrow()).contains("refused the bot's privileged intents");
    }

    @Test
    void reportsHowToInviteTheBotWhenItIsNotInTheServer() {
        discord.guildMember = false;

        bridge = start(TestSupport.settings(List.of()));

        awaitState(State.DEGRADED);
        assertThat(bridge.status().reason().orElseThrow())
                .contains("not in Discord server " + GUILD_ID)
                .contains("client_id=" + APPLICATION_ID + "&scope=bot+applications.commands");
    }

    @Test
    void finishesShutdownWithinThreeSecondsWhenDiscordStopsAnswering() {
        bridge = startReady();
        bridge.send(OutboundMessage.of(CHANNEL, STEVE, "warm up"));
        until("webhook ready", () -> executions().size() == 1);
        discord.stallMessages = true;
        bridge.send(OutboundMessage.of(CHANNEL, STEVE, "stuck"));
        until("stuck request", () -> executions().size() == 2);
        bridge.send(OutboundMessage.of(CHANNEL, STEVE, "queued"));

        long started = System.nanoTime();
        bridge.shutdown(
                List.of(OutboundMessage.of(CHANNEL, SERVER, "Server stopped")), Deadline.after(SHUTDOWN_BUDGET));

        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(SHUTDOWN_LIMIT);
        assertThat(bridge.status().state()).isEqualTo(State.STOPPED);
    }

    @Test
    void finishesShutdownWithinThreeSecondsWhileLoginHangs() {
        discord.stallLogin = true;
        bridge = start(TestSupport.settings(List.of()));
        until("login attempt", () -> discord.timeline.contains("GET users/@me"));

        long started = System.nanoTime();
        bridge.shutdown(
                List.of(OutboundMessage.of(CHANNEL, SERVER, "Server stopped")), Deadline.after(SHUTDOWN_BUDGET));

        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(SHUTDOWN_LIMIT);
        assertThat(bridge.status().state()).isEqualTo(State.STOPPED);
        assertThat(discord.timeline).doesNotContain("GATEWAY connect");
    }

    @Test
    void knowsTheOwnerAndAcceptedAdminsOrDevelopersOfTheTeam() {
        bridge = startReady();

        assertThat(bridge.isApplicationOwner(PLAYER)).isTrue();
        assertThat(bridge.isApplicationOwner(FRIEND)).isTrue();
        assertThat(bridge.isApplicationOwner(OTHER_BOT)).isFalse();
        assertThat(bridge.isApplicationOwner(new DiscordId(12345))).isFalse();
    }

    @Test
    void findsTheServerThroughTheConfiguredChannel() {
        bridge = start(unbound(Set.of(CHANNEL), List.of(LIST)));
        awaitState(State.READY);
        until("registration", () -> registrations().size() == 1);

        discord.dispatch("INTERACTION_CREATE", discord.command(OTHER_GUILD_ID, "list", 2, DataArray.empty()));
        discord.sendMessage(discord.message(PLAYER_ID, "hello"));

        assertThat(take(listener.messages).content()).isEqualTo("hello");
        until("refusal", () -> callbacks().size() == 1);
        assertThat(listener.commands).isEmpty();
    }

    @Test
    void reportsWhenNoConfiguredChannelIsVisible() {
        bridge = start(unbound(Set.of(new DiscordId(CHANNEL_ID + 1)), List.of(LIST)));

        awaitState(State.DEGRADED);
        assertThat(bridge.status().reason().orElseThrow())
                .contains("cannot see the chat or events channel set in config.yml");
        assertThat(registrations()).isEmpty();
    }
}
