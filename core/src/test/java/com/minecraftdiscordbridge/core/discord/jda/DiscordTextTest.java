package com.minecraftdiscordbridge.core.discord.jda;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DiscordTextTest {
    @Test
    void leavesShortTextAlone() {
        assertThat(DiscordText.fit("hello", 10)).isEqualTo("hello");
    }

    @Test
    void endsShortenedTextWithAnEllipsis() {
        assertThat(DiscordText.fit("abcdefghij", 5)).isEqualTo("abcd…");
    }

    @Test
    void neverSplitsAnEmojiWhenShortening() {
        assertThat(DiscordText.fit("abc😀def", 5)).isEqualTo("abc…");
    }

    @Test
    void escapesMarkdownInSpeakerNames() {
        assertThat(DiscordText.speakerLine("a_b*c~d`e|f\\", "hi")).isEqualTo("**a\\_b\\*c\\~d\\`e\\|f\\\\**: hi");
    }
}
