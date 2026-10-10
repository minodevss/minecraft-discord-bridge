package com.minecraftdiscordbridge.core.i18n;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.minecraftdiscordbridge.core.text.Format;
import com.minecraftdiscordbridge.core.text.TextRenderer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class LangTest {
    private static final Pattern TAG = Pattern.compile("<([a-z_-]+)>");
    private static final int DISCORD_DESCRIPTION_LIMIT = 100;

    @Test
    void bothLanguagesHaveTheSameKeys() {
        assertThat(Lang.of(Language.KO).keys()).isEqualTo(Lang.of(Language.EN).keys());
    }

    @ParameterizedTest
    @EnumSource(Language.class)
    void everyMinecraftMessageIsValidMiniMessageWithItsPlaceholders(Language language) {
        Lang lang = Lang.of(language);
        for (String key : lang.keys()) {
            if (!key.startsWith("game.")) {
                continue;
            }
            List<Format.Slot> slots = new ArrayList<>();
            placeholders(lang.raw(key)).forEach(name -> slots.add(Format.Slot.text(name, "x")));

            assertThatCode(() -> TextRenderer.plain(lang.component(key, slots.toArray(Format.Slot[]::new))))
                    .as(language + " " + key)
                    .doesNotThrowAnyException();
            assertThat(TextRenderer.plain(lang.component(key, slots.toArray(Format.Slot[]::new))))
                    .as(language + " " + key)
                    .doesNotContain("<", ">");
        }
    }

    @ParameterizedTest
    @EnumSource(Language.class)
    void slashCommandDescriptionsFitDiscordLimits(Language language) {
        Lang lang = Lang.of(language);
        lang.keys().stream()
                .filter(key -> key.startsWith("command."))
                .forEach(key -> assertThat(lang.raw(key))
                        .as(language + " " + key)
                        .isNotBlank()
                        .hasSizeLessThanOrEqualTo(DISCORD_DESCRIPTION_LIMIT));
    }

    @Test
    void fillsNumberedArgumentsOnceWithoutReadingThemAgain() {
        assertThat(Lang.fill("{0} and {1} {2} {x}", "a{1}", "b")).isEqualTo("a{1} and b {2} {x}");
    }

    @Test
    void readsKoreanAsUtf8() {
        assertThat(Lang.of(Language.KO).text("discord.list.empty")).isEqualTo("접속 중인 플레이어가 없습니다.");
    }

    @Test
    void choosesSingularOrPluralForms() {
        Lang english = Lang.of(Language.EN);

        assertThat(english.count("discord.status.online", 1)).isEqualTo("1 player online");
        assertThat(english.count("discord.status.online", 3)).isEqualTo("3 players online");
    }

    @Test
    void followsTheDiscordLocaleWhenItIsKnown() {
        Lang english = Lang.of(Language.EN);
        Lang korean = Lang.of(Language.KO);

        assertThat(Lang.forDiscordLocale("ko", english)).isSameAs(korean);
        assertThat(Lang.forDiscordLocale("en-GB", korean)).isSameAs(english);
        assertThat(Lang.forDiscordLocale("de", korean)).isSameAs(korean);
    }

    @Test
    void fallsBackToTheKeyForUnknownText() {
        assertThat(Lang.of(Language.KO).text("no.such.key")).isEqualTo("no.such.key");
    }

    private static Set<String> placeholders(String template) {
        Set<String> names = new LinkedHashSet<>();
        Matcher matcher = TAG.matcher(template);
        while (matcher.find()) {
            if (!TagResolver.standard().has(matcher.group(1))) {
                names.add(matcher.group(1));
            }
        }
        return names;
    }
}
