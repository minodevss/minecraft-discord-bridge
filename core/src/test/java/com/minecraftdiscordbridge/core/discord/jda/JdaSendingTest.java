package com.minecraftdiscordbridge.core.discord.jda;

import static com.minecraftdiscordbridge.core.discord.jda.DiscordPayloads.CHANNEL_ID;
import static com.minecraftdiscordbridge.core.discord.jda.DiscordPayloads.FRIEND_ID;
import static com.minecraftdiscordbridge.core.discord.jda.DiscordPayloads.GUILD_ID;
import static com.minecraftdiscordbridge.core.discord.jda.DiscordPayloads.ROLE_ID;
import static com.minecraftdiscordbridge.core.fakes.Waits.take;
import static com.minecraftdiscordbridge.core.fakes.Waits.until;
import static org.assertj.core.api.Assertions.assertThat;

import com.minecraftdiscordbridge.core.discord.Author;
import com.minecraftdiscordbridge.core.discord.DiscordId;
import com.minecraftdiscordbridge.core.discord.DiscordStatus.State;
import com.minecraftdiscordbridge.core.discord.OutboundMessage;
import com.minecraftdiscordbridge.core.discord.WebhookCredentials;
import com.minecraftdiscordbridge.core.loop.Deadline;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import mockwebserver3.RecordedRequest;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.utils.data.DataArray;
import net.dv8tion.jda.api.utils.data.DataObject;
import org.junit.jupiter.api.Test;

class JdaSendingTest extends JdaTestBase {
    @Test
    void sendsMessagesQueuedBeforeDiscordWasReady() {
        bridge = start(TestSupport.settings(List.of()));
        bridge.send(OutboundMessage.of(CHANNEL, SERVER, "Server started"));

        awaitState(State.READY);
        until("queued message", () -> executions().size() == 1);
        assertThat(DiscordPayloads.body(executions().get(0)).getString("content"))
                .isEqualTo("Server started");
        assertThat(discord.requests("POST", "channels/" + CHANNEL_ID + "/webhooks"))
                .hasSize(1);
    }

    @Test
    void sendsPlayerMessagesThroughOneWebhookItCreates() {
        bridge = startReady();

        bridge.send(new OutboundMessage(CHANNEL, STEVE, "hello <@" + FRIEND + ">", Set.of(FRIEND)));
        until("first message", () -> executions().size() == 1);
        bridge.send(OutboundMessage.of(CHANNEL, STEVE, "again"));
        until("second message", () -> executions().size() == 2);

        List<RecordedRequest> creations = discord.requests("POST", "channels/" + CHANNEL_ID + "/webhooks");
        assertThat(creations).hasSize(1);
        assertThat(DiscordPayloads.body(creations.get(0)).getString("name")).isEqualTo("Minecraft Bridge");
        DataObject first = DiscordPayloads.body(executions().get(0));
        assertThat(first.getString("content")).isEqualTo("hello <@" + FRIEND_ID + ">");
        assertThat(first.getString("username")).isEqualTo("Steve");
        assertThat(first.getString("avatar_url")).isEqualTo("https://mc-heads.net/avatar/Steve");
        DataObject mentions = first.getObject("allowed_mentions");
        assertThat(mentions.getArray("parse").length()).isZero();
        assertThat(mentions.getArray("users").getString(0)).isEqualTo(Long.toString(FRIEND_ID));
        assertThat(mentions.getBoolean("replied_user")).isFalse();
        assertThat(webhooks.stored).containsKey(CHANNEL);
    }

    @Test
    void recreatesTheWebhookOnceWhenDiscordNoLongerKnowsIt() {
        webhooks.stored.put(CHANNEL, new WebhookCredentials(new DiscordId(777), "stale"));
        discord.webhookGoneResponses.set(1);
        bridge = startReady();

        bridge.send(OutboundMessage.of(CHANNEL, STEVE, "hello"));

        until("resent message", () -> executions().size() == 2);
        assertThat(discord.requestsStartingWith("POST", "webhooks/777/")).hasSize(1);
        assertThat(discord.requests("POST", "channels/" + CHANNEL_ID + "/webhooks"))
                .hasSize(1);
        assertThat(webhooks.stored.get(CHANNEL).id()).isNotEqualTo(new DiscordId(777));
    }

    @Test
    void postsAsTheBotWhenItCannotManageWebhooks() {
        discord.everyonePermissions = DiscordPayloads.NO_WEBHOOKS;
        bridge = startReady();

        bridge.send(OutboundMessage.of(CHANNEL, STEVE, "hi"));

        until("bot message", () -> botMessages().size() == 1);
        DataObject body = DiscordPayloads.body(botMessages().get(0));
        assertThat(body.getString("content")).isEqualTo("**Steve**: hi");
        assertThat(body.getObject("allowed_mentions").getArray("parse").length())
                .isZero();
        assertThat(discord.requestsStartingWith("POST", "webhooks/")).isEmpty();
    }

    @Test
    void countsRateLimitedRequestsPerChannelAndStillDelivers() {
        discord.rateLimitedResponses.set(1);
        bridge = startReady();

        bridge.send(OutboundMessage.of(CHANNEL, STEVE, "hello"));

        until("retried message", () -> executions().size() == 2);
        until("429 count", () -> bridge.status().channels().get(CHANNEL).rateLimited() == 1);
        assertThat(bridge.status().channels().get(CHANNEL).name()).contains("minecraft");
    }

    @Test
    void setsEveryMessageMentionPolicyExplicitlyAndNeverAllowsEveryoneOrRoles() {
        bridge = start(TestSupport.settings(List.of(LIST, WHITELIST)));
        awaitState(State.READY);
        bridge.send(new OutboundMessage(CHANNEL, STEVE, "@everyone <@&" + ROLE + ">", Set.of(FRIEND)));
        discord.dispatch("INTERACTION_CREATE", discord.command(GUILD_ID, "list", 2, DataArray.empty()));
        take(listener.replies).reply("@here");
        discord.dispatch("INTERACTION_CREATE", discord.command(GUILD_ID, "whitelist", 2, DataArray.empty()));
        take(listener.replies).reply("@everyone");
        until(
                "all messages",
                () -> executions().size() == 1
                        && callbacks().size() == 2
                        && originalEdits().size() == 1);
        bridge.shutdown(
                List.of(OutboundMessage.of(CHANNEL, SERVER, "Server stopped @everyone")),
                Deadline.after(SHUTDOWN_BUDGET));
        bridge = null;

        List<DataObject> messages = new ArrayList<>();
        executions().forEach(request -> messages.add(DiscordPayloads.body(request)));
        originalEdits().forEach(request -> messages.add(DiscordPayloads.body(request)));
        callbacks().stream()
                .map(DiscordPayloads::body)
                .filter(callback -> callback.getInt("type") == 4)
                .forEach(callback -> messages.add(callback.getObject("data")));
        assertThat(messages).hasSize(4);
        for (DataObject message : messages) {
            DataObject mentions = message.getObject("allowed_mentions");
            assertThat(mentions.getArray("parse").length())
                    .as(message.toString())
                    .isZero();
            assertThat(mentions.hasKey("roles")).as(message.toString()).isFalse();
        }
    }

    @Test
    void sendsTheFinalMessageBeforeDisconnecting() {
        bridge = startReady();
        bridge.send(OutboundMessage.of(CHANNEL, STEVE, "bye"));
        until("first message", () -> executions().size() == 1);

        bridge.shutdown(
                List.of(OutboundMessage.of(CHANNEL, SERVER, "Server stopped")), Deadline.after(SHUTDOWN_BUDGET));

        assertThat(executions()).hasSize(2);
        DataObject last = DiscordPayloads.body(executions().get(1));
        assertThat(last.getString("content")).isEqualTo("Server stopped");
        assertThat(last.getString("username")).isEqualTo("Survival");
        assertThat(bridge.status().state()).isEqualTo(State.STOPPED);
    }

    @Test
    void staysConnectedUntilDiscordAnswersTheFinalMessage() {
        bridge = startReady();
        until("webhook", () -> webhooks.stored.containsKey(CHANNEL));
        JDA jda = bridge.currentJda();
        discord.holdWebhookAnswers = true;

        CompletableFuture<Void> stopping = CompletableFuture.runAsync(() -> bridge.shutdown(
                List.of(OutboundMessage.of(CHANNEL, SERVER, "Server stopped")), Deadline.after(SHUTDOWN_BUDGET)));
        until("final message", () -> executions().size() == 1);

        assertThat(jda.getStatus()).isEqualTo(JDA.Status.CONNECTED);
        assertThat(stopping).isNotDone();
        discord.answerHeldWebhooks();
        stopping.join();
        assertThat(discord.timeline).contains("GATEWAY close");
        assertThat(bridge.status().state()).isEqualTo(State.STOPPED);
    }

    @Test
    void allowsMentionsOfExactlyTheRequestedRoles() {
        bridge = startReady();

        bridge.send(new OutboundMessage(CHANNEL, SERVER, "<@&" + ROLE + "> Server started", Set.of(), Set.of(ROLE)));

        until("message", () -> executions().size() == 1);
        DataObject mentions = DiscordPayloads.body(executions().get(0)).getObject("allowed_mentions");
        assertThat(mentions.getArray("parse").length()).isZero();
        assertThat(mentions.getArray("roles").length()).isEqualTo(1);
        assertThat(mentions.getArray("roles").getString(0)).isEqualTo(Long.toString(ROLE_ID));
    }

    @Test
    void postsServerMessagesUnderTheBotsNameAndAvatarWhenNoneIsGiven() {
        bridge = startReady();

        bridge.send(OutboundMessage.of(CHANNEL, Author.server(), "Server started"));

        until("message", () -> executions().size() == 1);
        DataObject body = DiscordPayloads.body(executions().get(0));
        assertThat(body.getString("username")).isEqualTo("Bridge");
        assertThat(body.getString("avatar_url")).startsWith("https://cdn.discordapp.com/embed/avatars/");
    }
}
