package com.minecraftdiscordbridge.core.text;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Random;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class DiscordMarkdownPerformanceTest {
    private static final Duration BUDGET = Duration.ofMillis(200);

    @BeforeAll
    static void warmUp() {
        DiscordMarkdown.parse("**warm** _up_ ~~a~~ ||b|| `c` [d](https://e.example) <@123456789012345678>");
    }

    static Stream<String> adversarialInputs() {
        return Stream.of(
                fill("*"),
                fill("_"),
                fill("~"),
                fill("|"),
                fill("`"),
                fill("\\"),
                fill("["),
                fill("<"),
                fill("*a "),
                fill("**a"),
                fill("_a_b"),
                fill("a_"),
                fill("~~a"),
                fill("||a"),
                fill("[a]"),
                fill("[a]("),
                fill("[a](https://"),
                fill("<@1234567890123456"),
                fill("<t:"),
                fill("<https://a"),
                fill("https://"),
                fill("https://a)"),
                fill("```"),
                fill("``a`"),
                fill("> **a\n"),
                fill("# *a\n"),
                fill("\\*"),
                fill("*_~|`"),
                nested(),
                growingBacktickRuns(),
                randomMarkers());
    }

    @ParameterizedTest
    @MethodSource("adversarialInputs")
    void parsesAdversarialInputQuickly(String input) {
        assertThat(input).hasSize(DiscordMarkdown.MAX_LENGTH);

        long started = System.nanoTime();
        DiscordMarkdown.parse(input);
        Duration elapsed = Duration.ofNanos(System.nanoTime() - started);

        assertThat(elapsed).isLessThan(BUDGET);
    }

    private static String fill(String pattern) {
        return pattern.repeat(DiscordMarkdown.MAX_LENGTH / pattern.length() + 1)
                .substring(0, DiscordMarkdown.MAX_LENGTH);
    }

    private static String nested() {
        String open = "**__~~||*_".repeat(200);
        String close = new StringBuilder(open).reverse().toString();
        return fill(open + "x" + close);
    }

    private static String growingBacktickRuns() {
        StringBuilder text = new StringBuilder();
        for (int run = 1; text.length() < DiscordMarkdown.MAX_LENGTH; run++) {
            text.append("`".repeat(run)).append('a');
        }
        return text.substring(0, DiscordMarkdown.MAX_LENGTH);
    }

    private static String randomMarkers() {
        Random random = new Random(4000);
        String markers = "*_~|`[]()<>\\#-:@!/ a\n";
        StringBuilder text = new StringBuilder();
        while (text.length() < DiscordMarkdown.MAX_LENGTH) {
            text.append(markers.charAt(random.nextInt(markers.length())));
        }
        return text.toString();
    }
}
