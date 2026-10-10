package com.minecraftdiscordbridge.core.text;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.minecraftdiscordbridge.core.platform.RichText;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.ShadowColor;
import org.junit.jupiter.api.Test;

class TextRendererTest {
    private static final int MINECRAFT_1_20_1 = 3465;
    private static final int MINECRAFT_1_21_1 = 3955;
    private static final int MINECRAFT_1_21_4 = 4189;
    private static final int MINECRAFT_26_1 = 4786;
    private static final UUID PIG = new UUID(1, 2);

    private static final Component LINK = Component.text("hi")
            .clickEvent(ClickEvent.openUrl("https://example.com"))
            .hoverEvent(HoverEvent.showText(Component.text("tip")));

    @Test
    void writesCamelCaseEventsFor1_20_1() {
        assertThat(TextRenderer.toJson(LINK, MINECRAFT_1_20_1))
                .isEqualTo("{\"clickEvent\":{\"action\":\"open_url\",\"value\":\"https://example.com\"},"
                        + "\"hoverEvent\":{\"action\":\"show_text\",\"contents\":\"tip\"},\"text\":\"hi\"}");
    }

    @Test
    void writesEntityUuidAsStringFor1_20_1AndAsIntArrayFor1_21_1() {
        Component entity = Component.text("pig").hoverEvent(HoverEvent.showEntity(Key.key("minecraft:pig"), PIG));

        assertThat(TextRenderer.toJson(entity, MINECRAFT_1_20_1))
                .contains("\"id\":\"00000000-0000-0001-0000-000000000002\"");
        assertThat(TextRenderer.toJson(entity, MINECRAFT_1_21_1)).contains("\"id\":[0,1,0,2]");
    }

    @Test
    void writesCamelCaseEventsFor1_21_1() {
        assertThat(TextRenderer.toJson(LINK, MINECRAFT_1_21_1)).isEqualTo(TextRenderer.toJson(LINK, MINECRAFT_1_20_1));
    }

    @Test
    void writesShadowColorFrom1_21_4WithCamelCaseEvents() {
        Component shadowed = LINK.shadowColor(ShadowColor.shadowColor(0xFF000000));

        assertThat(TextRenderer.toJson(shadowed, MINECRAFT_1_21_1)).doesNotContain("shadow_color");
        assertThat(TextRenderer.toJson(shadowed, MINECRAFT_1_21_4))
                .contains("\"shadow_color\":-16777216")
                .contains("\"clickEvent\":{\"action\":\"open_url\",\"value\":\"https://example.com\"}");
    }

    @Test
    void writesSnakeCaseEventsFor26_1() {
        assertThat(TextRenderer.toJson(LINK, MINECRAFT_26_1))
                .isEqualTo("{\"click_event\":{\"action\":\"open_url\",\"url\":\"https://example.com\"},"
                        + "\"hover_event\":{\"action\":\"show_text\",\"value\":\"tip\"},\"text\":\"hi\"}");
    }

    @Test
    void addsHttpsSchemeToBareUrlsFor26_1() {
        Component bare = Component.text("x").clickEvent(ClickEvent.openUrl("example.com/a"));

        assertThat(TextRenderer.toJson(bare, MINECRAFT_26_1)).contains("\"url\":\"https://example.com/a\"");
        assertThat(TextRenderer.toJson(bare, MINECRAFT_1_21_4)).contains("\"value\":\"example.com/a\"");
    }

    @Test
    void writesPlainTextAsJsonString() {
        assertThat(TextRenderer.toJson(Component.text("plain"), MINECRAFT_26_1)).isEqualTo("\"plain\"");
    }

    @Test
    void readsCamelCaseAndSnakeCaseIntoTheSameComponent() {
        Component camel = TextRenderer.fromJson("{\"text\":\"hi\",\"color\":\"gold\","
                + "\"clickEvent\":{\"action\":\"open_url\",\"value\":\"https://example.com\"},"
                + "\"hoverEvent\":{\"action\":\"show_text\",\"contents\":\"tip\"}}");
        Component snake = TextRenderer.fromJson("{\"text\":\"hi\",\"color\":\"gold\","
                + "\"click_event\":{\"action\":\"open_url\",\"url\":\"https://example.com\"},"
                + "\"hover_event\":{\"action\":\"show_text\",\"value\":\"tip\"}}");

        assertThat(snake).isEqualTo(camel).isEqualTo(LINK.color(NamedTextColor.GOLD));
    }

    @Test
    void readsLegacyHoverValueField() {
        Component legacy = TextRenderer.fromJson(
                "{\"text\":\"hi\",\"hoverEvent\":{\"action\":\"show_text\",\"value\":{\"text\":\"tip\"}}}");

        assertThat(legacy.hoverEvent()).isEqualTo(HoverEvent.showText(Component.text("tip")));
    }

    @Test
    void readsEveryWrittenVersionBack() {
        for (int dataVersion : new int[] {MINECRAFT_1_20_1, MINECRAFT_1_21_1, MINECRAFT_1_21_4, MINECRAFT_26_1}) {
            assertThat(TextRenderer.fromJson(TextRenderer.toJson(LINK, dataVersion)))
                    .as("data version %d", dataVersion)
                    .isEqualTo(LINK);
        }
    }

    @Test
    void rejectsMalformedJsonWithClearMessage() {
        assertThatThrownBy(() -> TextRenderer.fromJson("{\"text\":"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Not a valid Minecraft text component");
        assertThatThrownBy(() -> TextRenderer.fromJson("null")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fallsBackToPlainTextWhenRichTextJsonIsBroken() {
        assertThat(TextRenderer.fromRichText(new RichText("{broken", "Steve fell")))
                .isEqualTo(Component.text("Steve fell"));
        assertThat(TextRenderer.fromRichText(new RichText("{\"text\":\"ok\"}", "ignored")))
                .isEqualTo(Component.text("ok"));
    }

    @Test
    void flattensTranslatableToFallbackThenKeyWithoutTranslations() {
        assertThat(TextRenderer.plain(Component.translatable("death.fell", "Steve fell")))
                .isEqualTo("Steve fell");
        assertThat(TextRenderer.plain(Component.translatable("death.fell"))).isEqualTo("death.fell");
    }

    @Test
    void flattensTranslatableWithSequentialArguments() {
        Translations english = Map.of("death.attack.player", "%s was slain by %s")::get;
        Component death =
                Component.translatable("death.attack.player", Component.text("Steve"), Component.text("Alex"));

        assertThat(TextRenderer.plain(death, english)).isEqualTo("Steve was slain by Alex");
    }

    @Test
    void flattensTranslatableWithPositionalArgumentsAndPercent() {
        Translations translations = Map.of("k", "%2$s before %1$s at 100%%")::get;
        Component component = Component.translatable("k", Component.text("a"), Component.text("b"));

        assertThat(TextRenderer.plain(component, translations)).isEqualTo("b before a at 100%");
    }

    @Test
    void flattensNestedTranslatableArgumentsAndChildren() {
        Translations translations = Map.of("outer", "<%s>", "inner", "[%s]")::get;
        Component component = Component.translatable("outer", Component.translatable("inner", Component.text("x")))
                .append(Component.text("!"));

        assertThat(TextRenderer.plain(component, translations)).isEqualTo("<[x]>!");
    }

    @Test
    void usesFallbackPatternWhenTranslationIsMissing() {
        Component component = Component.translatable()
                .key("custom.key")
                .fallback("%s joined")
                .arguments(Component.text("Steve"))
                .build();

        assertThat(TextRenderer.plain(component, Translations.NONE)).isEqualTo("Steve joined");
    }

    @Test
    void keepsRawPatternWhenFormatIsUnsupportedOrArgumentMissing() {
        Translations translations = Map.of("number", "%d items", "missing", "%s and %s", "stray", "50% off")::get;

        assertThat(TextRenderer.plain(Component.translatable("number", Component.text("3")), translations))
                .isEqualTo("%d items");
        assertThat(TextRenderer.plain(Component.translatable("missing", Component.text("a")), translations))
                .isEqualTo("%s and %s");
        assertThat(TextRenderer.plain(Component.translatable("stray"), translations))
                .isEqualTo("50% off");
    }
}
