package com.minecraftdiscordbridge.core.console;

import com.minecraftdiscordbridge.core.config.ConsoleConfig;
import com.minecraftdiscordbridge.core.discord.Author;
import com.minecraftdiscordbridge.core.discord.Discord;
import com.minecraftdiscordbridge.core.discord.DiscordId;
import com.minecraftdiscordbridge.core.discord.OutboundMessage;
import com.minecraftdiscordbridge.core.i18n.Lang;
import com.minecraftdiscordbridge.core.loop.CoreLoop;
import com.minecraftdiscordbridge.core.text.DiscordEscaper;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ConsoleMirror implements AutoCloseable {
    public static final Duration INTERVAL = Duration.ofSeconds(2);
    static final int MAX_MESSAGE_LENGTH = 2000;
    static final int MAX_MESSAGES_PER_FLUSH = 5;
    private static final String FENCE = "```";
    private static final int BODY_LENGTH = MAX_MESSAGE_LENGTH - 2 * FENCE.length() - 2;
    private static final Author SERVER = Author.server();
    private static final Logger LOGGER = LoggerFactory.getLogger("MinecraftDiscordBridge");

    private final ConsoleBuffer buffer;
    private final long channel;
    private final Discord discord;
    private final Lang lang;
    private final Supplier<Collection<String>> secrets;
    private final DateTimeFormatter time;
    private volatile @Nullable Runnable detach;
    private volatile @Nullable ScheduledFuture<?> timer;

    ConsoleMirror(
            ConsoleBuffer buffer,
            long channel,
            Discord discord,
            Lang lang,
            Supplier<Collection<String>> secrets,
            ZoneId zone) {
        this.buffer = buffer;
        this.channel = channel;
        this.discord = discord;
        this.lang = lang;
        this.secrets = secrets;
        this.time = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(zone);
    }

    public static Optional<ConsoleMirror> start(
            ConsoleConfig config,
            Optional<DiscordId> channel,
            Discord discord,
            Lang lang,
            CoreLoop loop,
            Supplier<Collection<String>> secrets) {
        if (!config.mirror() || channel.isEmpty()) {
            return Optional.empty();
        }
        ConsoleBuffer buffer = new ConsoleBuffer(ConsoleBuffer.CAPACITY);
        Optional<Runnable> detach = attach(buffer);
        if (detach.isEmpty()) {
            LOGGER.warn("Cannot mirror the console because this server does not log through Log4j 2."
                    + " Set console.mirror to false to hide this warning");
            return Optional.empty();
        }
        ConsoleMirror mirror =
                new ConsoleMirror(buffer, channel.get().value(), discord, lang, secrets, ZoneId.systemDefault());
        mirror.detach = detach.get();
        mirror.timer = loop.repeat(INTERVAL, mirror::flush);
        return Optional.of(mirror);
    }

    private static Optional<Runnable> attach(ConsoleBuffer buffer) {
        try {
            return Log4jHook.attach(buffer);
        } catch (LinkageError missingLog4j) {
            return Optional.empty();
        }
    }

    void flush() {
        Collection<String> hidden = secrets.get();
        List<String> lines = new ArrayList<>();
        long skipped = buffer.takeDropped();
        if (skipped > 0) {
            lines.add(lang.count("discord.console.skipped", skipped));
        }
        int budget = MAX_MESSAGES_PER_FLUSH * BODY_LENGTH;
        int used = 0;
        while (used < budget) {
            List<ConsoleLine> drained = buffer.drain(64);
            if (drained.isEmpty()) {
                break;
            }
            for (ConsoleLine line : drained) {
                String text = format(line, hidden);
                lines.add(text);
                used += text.length() + 1;
            }
        }
        blocks(lines).forEach(block -> discord.send(OutboundMessage.of(channel, SERVER, block)));
    }

    private String format(ConsoleLine line, Collection<String> hidden) {
        StringBuilder text = new StringBuilder()
                .append('[')
                .append(time.format(Instant.ofEpochMilli(line.timeMillis())))
                .append(' ')
                .append(line.level())
                .append("] ")
                .append(line.message());
        String thrown = line.thrown();
        if (thrown != null) {
            text.append('\n').append(thrown);
        }
        String redacted = DiscordEscaper.escapeCodeBlock(ConsoleRedactor.redact(text.toString(), hidden));
        return redacted.length() <= BODY_LENGTH ? redacted : redacted.substring(0, BODY_LENGTH - 1) + "…";
    }

    static List<String> blocks(List<String> lines) {
        List<String> blocks = new ArrayList<>();
        StringBuilder body = new StringBuilder();
        for (String line : lines) {
            if (body.length() > 0 && body.length() + 1 + line.length() > BODY_LENGTH) {
                blocks.add(fence(body));
                body.setLength(0);
            }
            if (body.length() > 0) {
                body.append('\n');
            }
            body.append(line);
        }
        if (body.length() > 0) {
            blocks.add(fence(body));
        }
        return blocks;
    }

    private static String fence(CharSequence body) {
        return FENCE + "\n" + body + "\n" + FENCE;
    }

    @Override
    public synchronized void close() {
        ScheduledFuture<?> running = timer;
        if (running != null) {
            running.cancel(false);
        }
        Runnable remove = detach;
        detach = null;
        if (remove != null) {
            remove.run();
        }
    }
}
