package com.minecraftdiscordbridge.core.text.markdown;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.minecraftdiscordbridge.core.text.Markup;
import java.util.stream.Stream;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class DiscordMarkdownTest {
    private static String render(String markdown) {
        return Markup.of(DiscordMarkdown.parse(markdown, FakeMentions.standard()));
    }

    static Stream<Arguments> stylesBoldItalicUnderlineAndStrikethroughMarkers() {
        return Stream.of(
                arguments("**bold**", "<bold>bold"),
                arguments("*italic*", "<italic>italic"),
                arguments("_italic_", "<italic>italic"),
                arguments("__underline__", "<underlined>underline"),
                arguments("~~strike~~", "<strikethrough>strike"),
                arguments("***both***", "<bold><italic>both"),
                arguments("___both___", "<underlined><italic>both"),
                arguments("*a **b** c*", "<italic>a <bold>b</bold> c"),
                arguments("**a** and **b**", "<bold>a</bold> and <bold>b"),
                arguments("**_mixed_ ~~nest~~**", "<bold><italic>mixed</italic> <strikethrough>nest"),
                arguments("a*b*c", "a<italic>b</italic>c"),
                arguments("snake_case_name", "snake_case_name"),
                arguments("a_b_", "a_b_"),
                arguments("(_word_)", "(<italic>word</italic>)"),
                arguments("*no closing space *", "*no closing space *"),
                arguments("* not italic*", "* not italic*"),
                arguments("**unclosed", "**unclosed"),
                arguments("**a*", "*<italic>a"),
                arguments("~~~~", "~~~~"),
                arguments("****", "****"),
                arguments("2 * 3 * 4", "2 * 3 * 4"),
                arguments("**multi\nline**", "<bold>multi\nline"));
    }

    @ParameterizedTest
    @MethodSource
    void stylesBoldItalicUnderlineAndStrikethroughMarkers(String markdown, String expected) {
        assertThat(render(markdown)).isEqualTo(expected);
    }

    static Stream<Arguments> showsCodeLiterallyInGray() {
        return Stream.of(
                arguments("`code`", "<gray>code"),
                arguments("`**not bold**`", "<gray>**not bold**"),
                arguments("``a`b``", "<gray>a`b"),
                arguments("`a\\`", "<gray>a\\\\"),
                arguments("```js\nlet x = 1;\n```", "<gray>let x = 1;"),
                arguments("```\nplain\n\n```", "<gray>plain"),
                arguments("```inline```", "<gray>inline"),
                arguments("```js\n```", "<gray>js"),
                arguments("```\n**raw** <@123456789012345678>\n```", "<gray>**raw** \\<@123456789012345678>"),
                arguments("`unclosed", "`unclosed"),
                arguments("```unclosed", "```unclosed"));
    }

    @ParameterizedTest
    @MethodSource
    void showsCodeLiterallyInGray(String markdown, String expected) {
        assertThat(render(markdown)).isEqualTo(expected);
    }

    static Stream<Arguments> showsEscapedMarkersAsText() {
        return Stream.of(
                arguments("\\*not italic\\*", "*not italic*"),
                arguments("\\_\\_", "__"),
                arguments("\\**a**", "**a**"),
                arguments("\\a\\1\\ ", "\\a\\1\\ "),
                arguments("\\<@123456789012345678>", "<@123456789012345678>"),
                arguments("\\# not a heading", "# not a heading"),
                arguments("\\|\\|not a spoiler||", "||not a spoiler||"),
                arguments("\\\\", "\\"));
    }

    @ParameterizedTest
    @MethodSource
    void showsEscapedMarkersAsText(String markdown, String expectedPlain) {
        Component parsed = DiscordMarkdown.parse(markdown);

        assertThat(Markup.plain(parsed)).isEqualTo(expectedPlain);
        assertThat(parsed.children())
                .allSatisfy(child -> assertThat(child.style().isEmpty()).isTrue());
    }

    @Test
    void escapedBackslashDoesNotEscapeTheNextMarker() {
        assertThat(Markup.plain(DiscordMarkdown.parse("\\\\*a*"))).isEqualTo("\\a");
        assertThat(render("\\\\*a*")).endsWith("<italic>a");
    }

    static Stream<Arguments> makesOnlyHttpLinksClickable() {
        return Stream.of(
                arguments(
                        "[text](https://example.com)",
                        "<underlined><click:open_url:'https://example.com'>"
                                + "<hover:show_text:'https://example.com'>text"),
                arguments(
                        "[text](<https://example.com>)",
                        "<underlined><click:open_url:'https://example.com'>"
                                + "<hover:show_text:'https://example.com'>text"),
                arguments(
                        "[**bold** link](http://example.com/x)",
                        "<underlined><click:open_url:'http://example.com/x'>"
                                + "<hover:show_text:'http://example.com/x'><bold>bold</bold> link"),
                arguments(
                        "[https://shown.example](https://real.example)",
                        "<underlined><click:open_url:'https://real.example'>"
                                + "<hover:show_text:'https://real.example'>https://shown.example"),
                arguments("[x](javascript:alert(1))", "[x](javascript:alert(1))"),
                arguments("[x](ftp://example.com)", "[x](ftp://example.com)"),
                arguments(
                        "[](https://example.com)",
                        "[](<underlined><click:open_url:'https://example.com'>"
                                + "<hover:show_text:'https://example.com'>https://example.com</hover></click></underlined>)"),
                arguments(
                        "see https://example.com/a_b_c.",
                        "see <underlined><click:open_url:'https://example.com/a_b_c'>"
                                + "<hover:show_text:'https://example.com/a_b_c'>https://example.com/a_b_c"
                                + "</hover></click></underlined>."),
                arguments(
                        "(https://en.wikipedia.org/wiki/Foo_(bar))",
                        "(<underlined><click:open_url:'https://en.wikipedia.org/wiki/Foo_(bar)'>"
                                + "<hover:show_text:'https://en.wikipedia.org/wiki/Foo_(bar)'>"
                                + "https://en.wikipedia.org/wiki/Foo_(bar)</hover></click></underlined>)"),
                arguments(
                        "<https://example.com>",
                        "<underlined><click:open_url:'https://example.com'>"
                                + "<hover:show_text:'https://example.com'>https://example.com"),
                arguments("<javascript:alert(1)>", "\\<javascript:alert(1)>"),
                arguments("https://exa|mple.com", "https://exa|mple.com"),
                arguments("https://", "https://"));
    }

    @ParameterizedTest
    @MethodSource
    void makesOnlyHttpLinksClickable(String markdown, String expected) {
        assertThat(render(markdown)).isEqualTo(expected);
    }

    static Stream<Arguments> showsMentionsEmojiAndCommandsByName() {
        return Stream.of(
                arguments("hi <@123456789012345678>", "hi @Steve"),
                arguments("<@!123456789012345678>", "@Steve"),
                arguments("<@999999999999999999>", "@unknown-user"),
                arguments("<@&223456789012345678>", "<#3498DB>@Admin"),
                arguments("<@&223456789012345679>", "@Helper"),
                arguments("<@&999999999999999999>", "@unknown-role"),
                arguments("<#323456789012345678>", "#general"),
                arguments("<#999999999999999999>", "#unknown-channel"),
                arguments("<:pog:123456789012345678>", ":pog:"),
                arguments("<a:wave:123456789012345678>", ":wave:"),
                arguments("</ping:123456789012345678>", "/ping"),
                arguments("</admin ban user:123456789012345678>", "/admin ban user"),
                arguments("<@123>", "\\<@123>"),
                arguments("<@99999999999999999999>", "\\<@99999999999999999999>"),
                arguments("<:x:123456789012345678>", "\\<:x:123456789012345678>"),
                arguments("@everyone", "@everyone"));
    }

    @ParameterizedTest
    @MethodSource
    void showsMentionsEmojiAndCommandsByName(String markdown, String expected) {
        assertThat(render(markdown)).isEqualTo(expected);
    }

    static Stream<Arguments> formatsTimestampsInUtc() {
        return Stream.of(
                arguments("<t:1700000000>", "14 November 2023 22:13 UTC"),
                arguments("<t:1700000000:t>", "22:13 UTC"),
                arguments("<t:1700000000:T>", "22:13:20 UTC"),
                arguments("<t:1700000000:d>", "2023-11-14"),
                arguments("<t:1700000000:D>", "14 November 2023"),
                arguments("<t:1700000000:f>", "14 November 2023 22:13 UTC"),
                arguments("<t:1700000000:F>", "Tuesday, 14 November 2023 22:13 UTC"),
                arguments("<t:1700000000:R>", "14 November 2023 22:13 UTC"),
                arguments("<t:-1:d>", "1969-12-31"),
                arguments("<t:1700000000:x>", "\\<t:1700000000:x>"),
                arguments("<t:999999999999999>", "\\<t:999999999999999>"));
    }

    @ParameterizedTest
    @MethodSource
    void formatsTimestampsInUtc(String markdown, String expected) {
        assertThat(render(markdown)).isEqualTo(expected);
    }

    static Stream<Arguments> stylesHeadingsQuotesAndSmallPrintAtLineStart() {
        return Stream.of(
                arguments("# Heading", "<bold>Heading"),
                arguments("## Second", "<bold>Second"),
                arguments("### Third *x*", "<bold>Third <italic>x"),
                arguments("#### Fourth", "#### Fourth"),
                arguments("#NoSpace", "#NoSpace"),
                arguments("# ", "# "),
                arguments("text # not heading", "text # not heading"),
                arguments("-# small print", "<gray>small print"),
                arguments("> quoted", "<gray>▍ </gray>quoted"),
                arguments("> a\nb", "<gray>▍ </gray>a\nb"),
                arguments(">>> a\nb", "<gray>▍ </gray>a\n<gray>▍ </gray>b"),
                arguments("> # heading in quote", "<gray>▍ </gray><bold>heading in quote"),
                arguments("> > not nested", "<gray>▍ </gray>> not nested"),
                arguments(">no space", ">no space"),
                arguments("- item", "- item"),
                arguments("* item *x*", "* item <italic>x"),
                arguments("1. first\n2. second", "1. first\n2. second"),
                arguments("a\n# b", "a\n<bold>b"),
                arguments("**a\n# b**", "<bold>a\n# b"));
    }

    @ParameterizedTest
    @MethodSource
    void stylesHeadingsQuotesAndSmallPrintAtLineStart(String markdown, String expected) {
        assertThat(render(markdown)).isEqualTo(expected);
    }

    @Test
    void hidesSpoilerBehindBlocksOfTheSameLength() {
        Component spoiler = DiscordMarkdown.parse("||secret||");

        assertThat(Markup.plain(spoiler)).isEqualTo("▒▒▒▒▒▒");
        assertThat(Markup.of(spoiler)).isEqualTo("<dark_gray><hover:show_text:'secret'>▒▒▒▒▒▒");
    }

    @Test
    void capsSpoilerBlocks() {
        Component spoiler = DiscordMarkdown.parse("||" + "x".repeat(500) + "||");

        assertThat(Markup.plain(spoiler)).isEqualTo("▒".repeat(32));
    }

    @Test
    void keepsFormattingInsideSpoilerHover() {
        assertThat(render("||**loud**||")).isEqualTo("<dark_gray><hover:show_text:'<bold>loud'>▒▒▒▒");
    }

    @Test
    void stripsControlAndBidiCharacters() {
        String hostile = "a" + (char) 0x202E + "b" + (char) 0x2066 + "c" + (char) 7 + (char) 0x85 + "d\te\nf";

        assertThat(Markup.plain(DiscordMarkdown.parse(hostile))).isEqualTo("abcd\te\nf");
    }

    @Test
    void stripsBidiCharactersFromResolvedNames() {
        FakeMentions sneaky = new FakeMentions(
                java.util.Map.of(FakeMentions.STEVE, "evets" + (char) 0x202E), java.util.Map.of(), java.util.Map.of());

        assertThat(Markup.plain(DiscordMarkdown.parse("<@123456789012345678>", sneaky)))
                .isEqualTo("@evets");
    }

    @Test
    void truncatesLongInputWithEllipsis() {
        String plain = Markup.plain(DiscordMarkdown.parse("a".repeat(5000)));

        assertThat(plain).hasSize(DiscordMarkdown.MAX_LENGTH).endsWith("a…");
    }

    @Test
    void doesNotSplitSurrogatePairWhenTruncating() {
        String input = "a".repeat(DiscordMarkdown.MAX_LENGTH - 2) + "😀" + "b".repeat(10);

        assertThat(Markup.plain(DiscordMarkdown.parse(input)))
                .isEqualTo("a".repeat(DiscordMarkdown.MAX_LENGTH - 2) + "…");
    }

    @Test
    void keepsTextLiteralBeyondTheDepthCap() {
        Component parsed = DiscordMarkdown.parse("**__~~deep~~__**", MentionResolver.NONE, 2);

        assertThat(Markup.of(parsed)).isEqualTo("<bold><underlined>~~deep~~");
    }

    @Test
    void returnsEmptyComponentForEmptyInput() {
        assertThat(DiscordMarkdown.parse("")).isEqualTo(Component.empty());
    }

    @Test
    void emitsOnlyHttpOpenUrlClickEvents() {
        String hostile = "[a](javascript:alert(1)) [b](https://ok.example) <file:///etc/passwd> "
                + "[c](HTTPS://loud.example) https://plain.example ||[d](http://spoiled.example)||";

        assertThat(Markup.clickEvents(DiscordMarkdown.parse(hostile)))
                .extracting(ClickEvent::action, event -> ((ClickEvent.Payload.Text) event.payload()).value())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(ClickEvent.Action.OPEN_URL, "https://ok.example"),
                        org.assertj.core.groups.Tuple.tuple(ClickEvent.Action.OPEN_URL, "HTTPS://loud.example"),
                        org.assertj.core.groups.Tuple.tuple(ClickEvent.Action.OPEN_URL, "https://plain.example"),
                        org.assertj.core.groups.Tuple.tuple(ClickEvent.Action.OPEN_URL, "http://spoiled.example"));
    }

    @Test
    void leavesUnparsableUrlsAsPlainText() {
        Component parsed = DiscordMarkdown.parse("https://bad^host.example");

        assertThat(Markup.clickEvents(parsed)).isEmpty();
        assertThat(Markup.plain(parsed)).isEqualTo("https://bad^host.example");
    }

    @Test
    void neverParsesMiniMessageTags() {
        assertThat(Markup.plain(DiscordMarkdown.parse("<red>hi</red> <click:run_command:/op me>x")))
                .isEqualTo("<red>hi</red> <click:run_command:/op me>x");
    }
}
