package com.minecraftdiscordbridge.core.text;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.Random;
import java.util.stream.Stream;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class DiscordEscaperTest {
    private static final String[] TOKENS = {
        "*",
        "**",
        "_",
        "__",
        "~",
        "~~",
        "`",
        "```",
        "|",
        "||",
        "\\",
        "\\\\",
        "[",
        "]",
        "(",
        ")",
        "<",
        ">",
        "#",
        "-#",
        "-",
        "+",
        ".",
        ":",
        "@",
        "&",
        "!",
        "/",
        "'",
        "\"",
        ",",
        ";",
        "a",
        "b",
        "Z",
        "0",
        "1",
        "42",
        "é",
        "日本",
        "😀",
        "\u200B",
        " ",
        "  ",
        "\t",
        "\n",
        "\n\n",
        "1. ",
        "- ",
        "* ",
        "+ ",
        "# ",
        "## ",
        "> ",
        ">>> ",
        "-# ",
        "https://example.com",
        "http://example.com/a_b*c",
        "https://en.wikipedia.org/wiki/Foo_(bar)",
        "https://",
        "http://x",
        "https://e.com/[x](y)",
        "https://a.b/~u|v`w",
        "https://a.b/c.",
        "(https://a.b)",
        "<@123456789012345678>",
        "<@&223456789012345678>",
        "<#323456789012345678>",
        "<:pog:123456789012345678>",
        "<t:1700000000:R>",
        "</ping:123456789012345678>",
        "[x](https://e.com)",
        "||s||",
        "<https://e.com>",
        "word_",
        "_word",
        "t:",
        "everyone"
    };

    static Stream<Arguments> escapesMarkdown() {
        return Stream.of(
                arguments("**bold**", "\\*\\*bold\\*\\*"),
                arguments("snake_case", "snake\\_case"),
                arguments("~~gone~~", "\\~\\~gone\\~\\~"),
                arguments("`code`", "\\`code\\`"),
                arguments("||spoiler||", "\\|\\|spoiler\\|\\|"),
                arguments("[a](b)", "\\[a\\]\\(b\\)"),
                arguments("<@123456789012345678>", "\\<@123456789012345678\\>"),
                arguments("<t:1700000000:R>", "\\<t:1700000000:R\\>"),
                arguments("<:pog:123456789012345678>", "\\<:pog:123456789012345678\\>"),
                arguments("back\\slash", "back\\\\slash"),
                arguments("@everyone", "@everyone"),
                arguments("plain text 123", "plain text 123"));
    }

    @ParameterizedTest
    @MethodSource
    void escapesMarkdown(String text, String expected) {
        assertThat(DiscordEscaper.escape(text)).isEqualTo(expected);
    }

    static Stream<Arguments> escapesLineStartMarkers() {
        return Stream.of(
                arguments("# heading", "\\# heading"),
                arguments("-# subtext", "\\-# subtext"),
                arguments("> quote", "\\> quote"),
                arguments(">>> quote", "\\>\\>\\> quote"),
                arguments("- item", "\\- item"),
                arguments("+ item", "\\+ item"),
                arguments("* item", "\\* item"),
                arguments("12. item", "12\\. item"),
                arguments("  # indented", "  \\# indented"),
                arguments("a\n# b\n- c", "a\n\\# b\n\\- c"),
                arguments("a # b - c 1. d", "a # b - c 1. d"));
    }

    @ParameterizedTest
    @MethodSource
    void escapesLineStartMarkers(String text, String expected) {
        assertThat(DiscordEscaper.escape(text)).isEqualTo(expected);
    }

    static Stream<Arguments> leavesUrlsIntact() {
        return Stream.of(
                arguments("see https://example.com/a_b*c~d", "see https://example.com/a_b*c~d"),
                arguments("(https://example.com)", "\\(https://example.com)"),
                arguments("[https://example.com]", "\\[https://example.com]"),
                arguments("https://en.wikipedia.org/wiki/Foo_(bar)", "https://en.wikipedia.org/wiki/Foo_(bar)"),
                arguments("https://example.com<@123456789012345678>", "https://example.com\\<@123456789012345678\\>"),
                arguments("https:// *x*", "https:// \\*x\\*"));
    }

    @ParameterizedTest
    @MethodSource
    void leavesUrlsIntact(String text, String expected) {
        assertThat(DiscordEscaper.escape(text)).isEqualTo(expected);
    }

    @Test
    void escapedTextParsesBackToTheSameText() {
        Random random = new Random(20261010L);
        for (int run = 0; run < 5000; run++) {
            String text = randomText(random);

            Component parsed = DiscordMarkdown.parse(DiscordEscaper.escape(text), FakeMentions.standard());

            assertThat(Markup.plain(parsed))
                    .as("plain text of escaped %s", text)
                    .isEqualTo(text);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"a```b", "```", "````", "trailing`", "`leading", "``", "plain", "x\n```\ny"})
    void keepsCodeBlockContentInsideOneBlock(String text) {
        String escaped = DiscordEscaper.escapeCodeBlock(text);
        Component parsed = DiscordMarkdown.parse("```\n" + escaped + "\n```");

        assertThat(escaped).doesNotContain("``");
        assertThat(escaped.replace("\u200B", "")).isEqualTo(text);
        assertThat(parsed.color()).isEqualTo(NamedTextColor.GRAY);
        assertThat(Markup.plain(parsed)).isEqualTo(escaped);
    }

    private static String randomText(Random random) {
        StringBuilder text = new StringBuilder();
        int tokens = random.nextInt(40);
        for (int i = 0; i < tokens; i++) {
            text.append(TOKENS[random.nextInt(TOKENS.length)]);
        }
        return text.toString();
    }
}
