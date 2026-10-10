package com.minecraftdiscordbridge.paper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CommandArgumentsTest {
    @Test
    void returnsNoArgumentsForBlankInput() {
        assertThat(CommandArguments.split("")).isEmpty();
        assertThat(CommandArguments.split("   ")).isEmpty();
    }

    @Test
    void splitsOnAnyRunOfWhitespace() {
        assertThat(CommandArguments.split("link  123\t456")).containsExactly("link", "123", "456");
    }

    @Test
    void ignoresLeadingAndTrailingWhitespace() {
        assertThat(CommandArguments.split("  status ")).containsExactly("status");
    }
}
