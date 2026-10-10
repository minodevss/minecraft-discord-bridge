package com.minecraftdiscordbridge.core.console;

import static org.assertj.core.api.Assertions.assertThat;

import com.minecraftdiscordbridge.core.discord.Author;
import com.minecraftdiscordbridge.core.discord.DiscordId;
import com.minecraftdiscordbridge.core.discord.OutboundMessage;
import com.minecraftdiscordbridge.core.fakes.RecordingDiscord;
import com.minecraftdiscordbridge.core.i18n.Lang;
import com.minecraftdiscordbridge.core.i18n.Language;
import java.time.ZoneOffset;
import java.util.List;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.message.SimpleMessage;
import org.junit.jupiter.api.Test;

class ConsoleMirrorTest {
    private static final DiscordId CHANNEL = new DiscordId(444444444444444444L);
    private static final long NOON = 43_200_000L;

    private final RecordingDiscord discord = new RecordingDiscord();
    private final ConsoleBuffer buffer = new ConsoleBuffer(10);
    private final ConsoleMirror mirror = new ConsoleMirror(
            buffer, CHANNEL, discord, Lang.of(Language.EN), () -> List.of("secret-token"), ZoneOffset.UTC);

    @Test
    void postsBufferedLinesInOneCodeBlockAsTheServer() {
        buffer.offer(new ConsoleLine(NOON, "INFO", "Done (3.2s)!", null));
        buffer.offer(new ConsoleLine(NOON + 1000, "WARN", "Can't keep up!", "java.io.IOException: disk"));

        mirror.flush();

        assertThat(discord.sent).singleElement().satisfies(message -> {
            assertThat(message.channelId()).isEqualTo(CHANNEL);
            assertThat(message.author()).isEqualTo(Author.server());
            assertThat(message.text())
                    .isEqualTo("```\n[12:00:00 INFO] Done (3.2s)!\n[12:00:01 WARN] Can't keep up!\n"
                            + "java.io.IOException: disk\n```");
        });
    }

    @Test
    void keepsLinesFromClosingTheCodeBlockAndRedactsThem() {
        buffer.offer(new ConsoleLine(NOON, "INFO", "``` secret-token from 10.0.0.1", null));

        mirror.flush();

        assertThat(discord.sentMessages().get(0).text())
                .doesNotContain("secret-token", "10.0.0.1")
                .contains("`\u200B`\u200B` [hidden] from [ip]");
    }

    @Test
    void postsNothingWhenThereIsNothingNew() {
        mirror.flush();

        assertThat(discord.sent).isEmpty();
    }

    @Test
    void splitsLongOutputIntoMessagesThatFitDiscord() {
        ConsoleBuffer large = new ConsoleBuffer(100);
        ConsoleMirror chatty =
                new ConsoleMirror(large, CHANNEL, discord, Lang.of(Language.EN), List::of, ZoneOffset.UTC);
        for (int i = 0; i < 30; i++) {
            large.offer(new ConsoleLine(NOON, "INFO", "x".repeat(300), null));
        }

        chatty.flush();

        assertThat(discord.sent).hasSizeGreaterThan(1);
        assertThat(discord.sent)
                .allSatisfy(message -> assertThat(message.text())
                        .hasSizeLessThanOrEqualTo(OutboundMessage.MAX_LENGTH)
                        .startsWith("```\n")
                        .endsWith("\n```"));
    }

    @Test
    void shortensLongLinesWithoutSplittingAnEmoji() {
        String prefix = "[12:00:00 INFO] ";
        int highSurrogateAt = OutboundMessage.MAX_LENGTH - 10;
        buffer.offer(new ConsoleLine(NOON, "INFO", "x".repeat(highSurrogateAt - prefix.length()) + "😀 tail", null));

        mirror.flush();

        String text = discord.sentMessages().get(0).text();
        assertThat(text).endsWith("x…\n```");
        assertThat(text.codePoints().filter(c -> c >= Character.MIN_SURROGATE && c <= Character.MAX_SURROGATE))
                .isEmpty();
    }

    @Test
    void postsAtMostFiveMessagesPerFlushAndKeepsTheRestForLater() {
        ConsoleBuffer large = new ConsoleBuffer(100);
        ConsoleMirror chatty =
                new ConsoleMirror(large, CHANNEL, discord, Lang.of(Language.EN), List::of, ZoneOffset.UTC);
        for (int i = 0; i < 64; i++) {
            large.offer(new ConsoleLine(NOON, "INFO", i + " " + "x".repeat(1900), null));
        }

        chatty.flush();
        assertThat(discord.sent).hasSize(ConsoleMirror.MAX_MESSAGES_PER_FLUSH);

        for (int flush = 0; flush < 20; flush++) {
            chatty.flush();
        }
        assertThat(discord.sent).hasSize(64);
        assertThat(discord.sentMessages().get(5).text()).startsWith("```\n[12:00:00 INFO] 5 x");
    }

    @Test
    void countsLinesDroppedWhileTheBufferWasFull() {
        for (int i = 0; i < 13; i++) {
            buffer.offer(new ConsoleLine(NOON, "INFO", "line " + i, null));
        }

        mirror.flush();

        assertThat(discord.sentMessages().get(0).text())
                .startsWith("```\n(3 console lines skipped)\n[12:00:00 INFO] line 0");
    }

    @Test
    void appenderSkipsTheBridgeAndDiscordLibraryLoggers() {
        ConsoleAppender appender = new ConsoleAppender(buffer);

        appender.append(event("MinecraftDiscordBridge", "own"));
        appender.append(event("net.dv8tion.jda.internal.JDAImpl", "jda"));
        appender.append(event("okhttp3.OkHttpClient", "http"));
        appender.append(event("com.minecraftdiscordbridge.libs.net.dv8tion.jda.Foo", "relocated"));
        appender.append(event("net.minecraft.server.MinecraftServer", "Done"));

        assertThat(buffer.drain(10)).extracting(ConsoleLine::message).containsExactly("Done");
    }

    @Test
    void mirrorsTheRootLoggerOnlyWhileAttached() {
        Runnable detach = Log4jHook.attach(buffer).orElseThrow();
        LogManager.getLogger("net.minecraft.server.MinecraftServer").error("Can't keep up!");
        detach.run();
        LogManager.getLogger("net.minecraft.server.MinecraftServer").error("after");

        assertThat(buffer.drain(10)).extracting(ConsoleLine::message).containsExactly("Can't keep up!");
    }

    private static Log4jLogEvent event(String logger, String text) {
        return Log4jLogEvent.newBuilder()
                .setLoggerName(logger)
                .setLevel(Level.INFO)
                .setMessage(new SimpleMessage(text))
                .setTimeMillis(NOON)
                .build();
    }
}
