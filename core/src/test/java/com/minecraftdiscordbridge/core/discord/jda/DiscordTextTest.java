package com.minecraftdiscordbridge.core.discord.jda;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DiscordTextTest {
    @Test
    void escapesMarkdownInSpeakerNames() {
        assertThat(DiscordText.speakerLine("a_b*c~d`e|f\\", "hi")).isEqualTo("**a\\_b\\*c\\~d\\`e\\|f\\\\**: hi");
    }
}
