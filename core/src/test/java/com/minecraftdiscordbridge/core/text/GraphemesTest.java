package com.minecraftdiscordbridge.core.text;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GraphemesTest {
    @Test
    void leavesTextThatFitsAlone() {
        assertThat(Graphemes.fit("hello", 5, "…")).isEqualTo("hello");
    }

    @Test
    void cutsPlainTextAtTheLimitAndAddsTheEllipsis() {
        assertThat(Graphemes.fit("abcdefghij", 5, "…")).isEqualTo("abcd…");
        assertThat(Graphemes.fit("abcdefghij", 5)).isEqualTo("abcde");
    }

    @Test
    void neverSplitsASurrogatePair() {
        assertThat(Graphemes.fit("abc😀def", 4)).isEqualTo("abc");
    }

    @Test
    void keepsJoinedEmojiWhole() {
        String family = "👨‍👩‍👧";

        for (int limit = 3; limit < 2 + family.length(); limit++) {
            assertThat(Graphemes.fit("ab" + family + "cd", limit)).isEqualTo("ab");
        }
    }

    @Test
    void keepsSkinTonesVariationSelectorsAndKeycapsWithTheirBase() {
        assertThat(Graphemes.fit("ab👍🏽cd", 4)).isEqualTo("ab");
        assertThat(Graphemes.fit("ab❤️cd", 3)).isEqualTo("ab");
        assertThat(Graphemes.fit("ab1️⃣cd", 4)).isEqualTo("ab");
    }

    @Test
    void keepsFlagsWhole() {
        String korea = "🇰🇷";
        String flags = korea + "🇺🇸";

        assertThat(Graphemes.fit(flags + "!", 6)).isEqualTo(korea);
        assertThat(Graphemes.fit(flags + "!", 8)).isEqualTo(flags);
    }

    @Test
    void keepsCombiningAccentsWithTheirLetter() {
        assertThat(Graphemes.fit("café!", 4)).isEqualTo("caf");
    }
}
