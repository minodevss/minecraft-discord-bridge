package com.minecraftdiscordbridge.core.discord.jda;

import static com.minecraftdiscordbridge.core.log.Log.LOGGER;

import com.minecraftdiscordbridge.core.log.Failures;
import com.minecraftdiscordbridge.core.loop.Scheduler;
import com.minecraftdiscordbridge.core.text.Graphemes;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.channel.middleman.StandardGuildMessageChannel;
import org.jspecify.annotations.Nullable;

final class Appearance {
    static final Duration PRESENCE_INTERVAL = Duration.ofSeconds(15);
    static final Duration TOPIC_INTERVAL = Duration.ofMinutes(10);
    private static final int MAX_STATUS_LENGTH = 128;
    private static final int MAX_TOPIC_LENGTH = 1024;

    private final Supplier<@Nullable JDA> jda;
    private final LongSupplier nanoClock;
    private final Scheduler scheduler;
    private final Throttle<String> presence;
    private final Map<Long, Throttle<String>> topics = new HashMap<>();
    private final Set<Long> topicLocked = new HashSet<>();

    Appearance(Supplier<@Nullable JDA> jda, LongSupplier nanoClock, Scheduler scheduler) {
        this.jda = jda;
        this.nanoClock = nanoClock;
        this.scheduler = scheduler;
        this.presence = new Throttle<>(PRESENCE_INTERVAL, nanoClock, scheduler, this::applyPresence);
    }

    void presence(String text) {
        presence.submit(text);
    }

    void topic(long channelId, String topic) {
        topics.computeIfAbsent(
                        channelId,
                        id -> new Throttle<>(TOPIC_INTERVAL, nanoClock, scheduler, text -> applyTopic(id, text)))
                .submit(topic);
    }

    void reapply() {
        presence.reapply();
        topics.values().forEach(Throttle::reapply);
    }

    private void applyPresence(String text) {
        JDA api = jda.get();
        if (api != null) {
            api.getPresence()
                    .setActivity(Activity.customStatus(Graphemes.fit(text, MAX_STATUS_LENGTH, Graphemes.ELLIPSIS)));
        }
    }

    private void applyTopic(long channelId, String topic) {
        JDA api = jda.get();
        StandardGuildMessageChannel channel =
                api == null ? null : api.getChannelById(StandardGuildMessageChannel.class, channelId);
        if (channel == null) {
            return;
        }
        if (!channel.getGuild().getSelfMember().hasPermission(channel, Permission.MANAGE_CHANNEL)) {
            if (topicLocked.add(channelId)) {
                LOGGER.warn(
                        "Give the bot the Manage Channel permission in #{} so it can update the topic",
                        channel.getName());
            }
            return;
        }
        if (topicLocked.remove(channelId)) {
            LOGGER.info("The bot can update the topic of #{} again", channel.getName());
        }
        String fitted = Graphemes.fit(topic, MAX_TOPIC_LENGTH, Graphemes.ELLIPSIS);
        if (fitted.equals(channel.getTopic())) {
            return;
        }
        channel.getManager()
                .setTopic(fitted)
                .queue(
                        null,
                        failure -> LOGGER.warn(
                                "Could not update the topic of #{}: {}",
                                channel.getName(),
                                Failures.describe(failure)));
    }
}
