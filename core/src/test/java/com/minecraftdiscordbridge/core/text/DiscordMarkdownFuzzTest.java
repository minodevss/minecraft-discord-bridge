package com.minecraftdiscordbridge.core.text;

import static org.assertj.core.api.Assertions.assertThat;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import java.net.URI;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;

class DiscordMarkdownFuzzTest {
    @FuzzTest
    void parsesArbitraryInputSafely(FuzzedDataProvider data) {
        String markdown = data.consumeRemainingAsString();

        Component parsed = DiscordMarkdown.parse(markdown, FakeMentions.standard());

        assertThat(Markup.clickEvents(parsed)).allSatisfy(DiscordMarkdownFuzzTest::assertHttpLink);
        assertThat(Markup.plain(parsed).chars().noneMatch(c -> SafeText.isUnsafe((char) c)))
                .as("no control or bidi characters")
                .isTrue();
        assertThat(Markup.plain(DiscordMarkdown.parse(DiscordEscaper.escape(Markup.plain(parsed)))))
                .isEqualTo(Markup.plain(parsed));
    }

    private static void assertHttpLink(ClickEvent event) {
        assertThat(event.action()).isEqualTo(ClickEvent.Action.OPEN_URL);
        URI uri = URI.create(((ClickEvent.Payload.Text) event.payload()).value());
        assertThat(uri.getScheme().toLowerCase(Locale.ROOT)).isIn("http", "https");
    }
}
