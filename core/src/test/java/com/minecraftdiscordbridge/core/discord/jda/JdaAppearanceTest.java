package com.minecraftdiscordbridge.core.discord.jda;

import static com.minecraftdiscordbridge.core.fakes.Waits.stays;
import static com.minecraftdiscordbridge.core.fakes.Waits.until;
import static org.assertj.core.api.Assertions.assertThat;

import com.minecraftdiscordbridge.core.fakes.RecordingLogs;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import net.dv8tion.jda.api.utils.data.DataObject;
import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

class JdaAppearanceTest extends JdaTestBase {
    @Test
    void updatesPresenceOnlyWhenTheTextChanges() throws Exception {
        bridge = startReady();

        bridge.updatePresence("3 players online");
        until("presence", () -> discord.presences.size() == 1);
        bridge.updatePresence("3 players online");

        stays("one presence update", () -> discord.presences.size() == 1);
        DataObject activity = discord.presences.get(0).getArray("activities").getObject(0);
        assertThat(activity.getString("state")).isEqualTo("3 players online");
    }

    @Test
    void updatesTheChannelTopicOnlyWhenItChanges() throws Exception {
        bridge = startReady();

        bridge.updateTopic(CHANNEL, "3/20 players online");
        until("topic", () -> topicEdits().size() == 1);
        bridge.updateTopic(CHANNEL, "3/20 players online");
        bridge.updateTopic(CHANNEL, "4/20 players online");

        stays("one topic edit", () -> topicEdits().size() == 1);
        assertThat(DiscordPayloads.body(topicEdits().get(0)).getString("topic")).isEqualTo("3/20 players online");
    }

    @Test
    void setsATopicAskedForBeforeTheConnectionWasReady() throws InterruptedException {
        bridge = new JdaDiscord(
                TestSupport.settings(List.of()),
                discord.endpoints(),
                loop,
                listener,
                webhooks,
                commandHashes,
                TestSupport.ENGLISH);
        bridge.updateTopic(CHANNEL, "3/20 players online");
        CountDownLatch applied = new CountDownLatch(1);
        loop.control(applied::countDown);
        assertThat(applied.await(5, TimeUnit.SECONDS)).isTrue();

        bridge.start();

        until("topic", () -> topicEdits().size() == 1);
        assertThat(DiscordPayloads.body(topicEdits().get(0)).getString("topic")).isEqualTo("3/20 players online");
    }

    @Test
    void warnsOnceWhileTheBotCannotChangeTheTopic() {
        discord.everyonePermissions = DiscordPayloads.NO_WEBHOOKS;
        bridge = startReady();
        AtomicLong now = new AtomicLong();
        Appearance appearance = new Appearance(bridge::currentJda, now::get, (delay, task) -> {});

        try (RecordingLogs.Capture logs = RecordingLogs.capture()) {
            for (int players = 1; players <= 3; players++) {
                appearance.topic(CHANNEL, players + " players online");
                now.addAndGet(Appearance.TOPIC_INTERVAL.toNanos());
            }

            assertThat(logs.lines(Level.WARN))
                    .filteredOn(line -> line.contains("Manage Channel"))
                    .hasSize(1);
        }
        assertThat(topicEdits()).isEmpty();
    }
}
