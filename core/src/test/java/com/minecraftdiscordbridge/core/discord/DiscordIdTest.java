package com.minecraftdiscordbridge.core.discord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DiscordIdTest {
    @Test
    void readsSeventeenToTwentyDigits() {
        assertThat(DiscordId.parse("12345678901234567")).contains(new DiscordId(12345678901234567L));
        assertThat(DiscordId.parse("18446744073709551615"))
                .map(DiscordId::toString)
                .contains("18446744073709551615");
    }

    @Test
    void rejectsTextThatIsNotASnowflake() {
        assertThat(DiscordId.parse("")).isEmpty();
        assertThat(DiscordId.parse("1234567890123456")).isEmpty();
        assertThat(DiscordId.parse("123456789012345678901")).isEmpty();
        assertThat(DiscordId.parse("+12345678901234567")).isEmpty();
        assertThat(DiscordId.parse("12345678901234567a")).isEmpty();
        assertThat(DiscordId.parse("１２３４５６７８９０１２３４５６７")).isEmpty();
    }

    @Test
    void rejectsTwentyDigitsAboveTheLargestId() {
        assertThat(DiscordId.parse("18446744073709551616")).isEmpty();
        assertThat(DiscordId.parse("99999999999999999999")).isEmpty();
    }

    @Test
    void namesTheFieldWhenRequiredTextIsNotAnId() {
        assertThatThrownBy(() -> DiscordId.require("general", "chat channel"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("chat channel must be a Discord ID of 17 to 20 digits, got \"general\"");
    }

    @Test
    void printsAsUnsignedDigits() {
        assertThat(new DiscordId(-1L)).hasToString("18446744073709551615");
    }
}
