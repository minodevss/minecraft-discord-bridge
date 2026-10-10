package com.minecraftdiscordbridge.core.console;

import static com.minecraftdiscordbridge.core.log.Log.LOGGER;

import com.minecraftdiscordbridge.core.config.ConsoleConfig;
import com.minecraftdiscordbridge.core.discord.Author;
import com.minecraftdiscordbridge.core.discord.Discord;
import com.minecraftdiscordbridge.core.discord.DiscordId;
import com.minecraftdiscordbridge.core.discord.OutboundMessage;
import com.minecraftdiscordbridge.core.i18n.Lang;
import com.minecraftdiscordbridge.core.loop.CoreLoop;
import com.minecraftdiscordbridge.core.text.Graphemes;
import com.minecraftdiscordbridge.core.text.markdown.DiscordEscaper;
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

public final class ConsoleMirror implements AutoCloseable {
    public static final Duration INTERVAL = Duration.ofSeconds(2);
    static final int MAX_MESSAGE_LENGTH = 2000;
    static final int MAX_MESSAGES_PER_FLUSH = 5;
    private static final String FENCE = "```";
    private static final int BODY_LENGTH = MAX_MESSAGE_LENGTH - 2 * FENCE.length() - 2;
    private static final Author SERVER = Author.server();

    private final ConsoleBuffer buffer;
    private final long channel;
    private final Discord discord;
    private final Lang lang;
    private final Supplier<Collection<String>> secrets;
    private final DateTimeFormatter time;
    private @Nullable String carried;
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
        Blocks blocks = new Blocks();
        long skipped = buffer.takeDropped();
        if (skipped > 0) {
            blocks.add(lang.count("discord.console.skipped", skipped));
        }
        String line = carried != null ? carried : next(hidden);
        while (line != null && blocks.add(line)) {
            line = next(hidden);
        }
        carried = line;
        blocks.finish().forEach(block -> discord.send(OutboundMessage.of(channel, SERVER, block)));
    }

    private @Nullable String next(Collection<String> hidden) {
        List<ConsoleLine> drained = buffer.drain(1);
        return drained.isEmpty() ? null : format(drained.get(0), hidden);
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
        return Graphemes.fit(redacted, BODY_LENGTH, "…");
    }

    private static final class Blocks {
        private final List<String> full = new ArrayList<>();
        private final StringBuilder body = new StringBuilder();

        boolean add(String line) {
            if (body.length() > 0 && body.length() + 1 + line.length() > BODY_LENGTH) {
                if (full.size() + 1 >= MAX_MESSAGES_PER_FLUSH) {
                    return false;
                }
                full.add(fence(body));
                body.setLength(0);
            }
            if (body.length() > 0) {
                body.append('\n');
            }
            body.append(line);
            return true;
        }

        List<String> finish() {
            if (body.length() > 0) {
                full.add(fence(body));
            }
            return full;
        }

        private static String fence(CharSequence body) {
            return FENCE + "\n" + body + "\n" + FENCE;
        }
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
