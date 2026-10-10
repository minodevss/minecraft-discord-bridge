package com.minecraftdiscordbridge.core.text;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.minecraftdiscordbridge.core.text.markdown.DiscordMarkdown;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class FormatTest {
    private static final Format CHAT = Format.compile("chat", "<gray><author>: <message>", Set.of("author", "message"));

    @Test
    void insertsTextWithoutParsingTags() {
        Component rendered = CHAT.render(
                Format.Slot.text("author", "<red>Steve"),
                Format.Slot.text("message", "<click:run_command:'/op me'>hi</click> &cRed"));

        assertThat(Markup.plain(rendered)).isEqualTo("<red>Steve: <click:run_command:'/op me'>hi</click> &cRed");
        assertThat(Markup.clickEvents(rendered)).isEmpty();
    }

    @Test
    void insertsComponentsWithTheirStyle() {
        Component rendered = CHAT.render(
                Format.Slot.text("author", "Steve"), Format.Slot.component("message", DiscordMarkdown.parse("**hi**")));

        assertThat(Markup.of(rendered)).isEqualTo("<gray>Steve: <bold>hi");
    }

    @Test
    void appliesTemplateStyleToInsertedText() {
        Component rendered = CHAT.render(Format.Slot.text("author", "Steve"), Format.Slot.text("message", "hi"));

        assertThat(rendered.color()).isEqualTo(NamedTextColor.GRAY);
    }

    @Test
    void rendersMissingSlotsAsEmpty() {
        assertThat(Markup.plain(CHAT.render(Format.Slot.text("author", "Steve"))))
                .isEqualTo("Steve: ");
    }

    @Test
    void stripsControlAndBidiCharactersFromText() {
        Component rendered = CHAT.render(Format.Slot.text("author", "Ste" + (char) 0x202E + "ve" + (char) 7));

        assertThat(Markup.plain(rendered)).isEqualTo("Steve: ");
    }

    @Test
    void rejectsSlotsTheFormatDoesNotDeclare() {
        assertThatThrownBy(() -> CHAT.render(Format.Slot.text("player", "Steve")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Format 'chat' has no placeholder <player>");
    }

    @Test
    void namesTheFormatAndTheUnknownTag() {
        assertThatThrownBy(() -> Format.compile("join", "<yellow><plyer> joined", Set.of("player")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Format 'join' is not valid MiniMessage: unknown tag <plyer>. "
                        + "Available placeholders: <player>.");
    }

    @Test
    void namesTheFormatAndTheInvalidTagArgument() {
        assertThatThrownBy(() -> Format.compile("leave", "<color:notacolor><player> left", Set.of("player")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Format 'leave' is not valid MiniMessage: tag <color> is invalid");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "<#ff0000>a <!italic>b <reset>c",
                "<hover:show_text:'<gray>tip'>d</hover>",
                "<gradient:red:blue>e</gradient> <rainbow>f",
                "<click:open_url:'https://example.com'>g",
                "\\<escaped> tags and 3 < 4 > 2",
                "<newline><br><bold><italic><underlined><strikethrough><obfuscated>",
                "plain"
            })
    void acceptsStandardTemplates(String template) {
        assertThat(Format.compile("ok", template, Set.of()).template()).isEqualTo(template);
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "Message | may only use lowercase letters",
                "has space | may only use lowercase letters",
                "red | has the same name as a MiniMessage tag",
                "bold | has the same name as a MiniMessage tag"
            })
    void rejectsUnusablePlaceholderNames(String placeholder, String problem) {
        assertThatThrownBy(() -> Format.compile("chat", "<" + placeholder + ">", Set.of(placeholder)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("of format 'chat'")
                .hasMessageContaining(problem);
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {"&aHello | &a", "<gray>&lBold | &l", "&#ff8800Orange | &#ff8800", "&rReset | &r"})
    void warnsAboutLegacyColorCodes(String template, String code) {
        assertThat(Format.compile("motd", template, Set.of()).legacyCodeWarning())
                .hasValue("Format 'motd' contains the legacy color code '" + code
                        + "', which shows as plain text. Use MiniMessage tags such as <green> or <bold> instead.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"§cHi", "<gray>§LBold"})
    void rejectsSectionSignCodesThatMiniMessageCannotRead(String template) {
        assertThatThrownBy(() -> Format.compile("motd", template, Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Format 'motd' uses the legacy color code '§")
                .hasMessageEndingWith("Use MiniMessage tags such as <green> or <bold> instead.");
    }

    @Test
    void reportsEveryProblemOfATemplate() {
        assertThatThrownBy(() -> Format.compile("chat", "<nmae> <color:nope>x</color> </msg>", Set.of("name")))
                .hasMessageContaining("unknown tag <nmae>")
                .hasMessageContaining("unknown tag <msg>")
                .hasMessageContaining("tag <color> is invalid (Unable to parse a color from 'nope'");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Rock & roll", "<green>fine", "Q&A", "a&", "price: 5§"})
    void doesNotWarnWithoutLegacyCodes(String template) {
        assertThat(Format.compile("motd", template, Set.of()).legacyCodeWarning())
                .isEmpty();
    }
}
