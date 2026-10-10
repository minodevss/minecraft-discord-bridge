package com.minecraftdiscordbridge.core.codes;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CodesAndAttemptsTest {
    private static final Instant NOW = Instant.parse("2026-10-10T00:00:00Z");

    @Test
    void makesEightCharacterCodesWithoutLookAlikeCharacters() {
        Codes codes = Codes.secure();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            String code = codes.next();
            assertThat(code).hasSize(8).matches("[A-HJ-NP-Z2-9]{8}");
            seen.add(code);
        }

        assertThat(Codes.ALPHABET).hasSize(32).doesNotContain("0", "1", "I", "O");
        assertThat(seen).hasSizeGreaterThan(990);
    }

    @Test
    void readsCodesTypedWithSpacesDashesOrLowercase() {
        assertThat(Codes.normalize(" abcd-efgh ")).isEqualTo("ABCDEFGH");
    }

    @Test
    void blocksAUserAfterFiveFailuresUntilTheWindowPasses() {
        Attempts attempts = Attempts.standard();
        for (int i = 0; i < 4; i++) {
            attempts.failed(1, NOW);
        }
        assertThat(attempts.blocked(1, NOW)).isFalse();

        attempts.failed(1, NOW);

        assertThat(attempts.blocked(1, NOW)).isTrue();
        assertThat(attempts.blocked(2, NOW)).isFalse();
        assertThat(attempts.blocked(1, NOW.plus(Duration.ofMinutes(10)))).isFalse();
    }

    @Test
    void blocksEveryoneAfterTooManyFailuresOverall() {
        Attempts attempts = new Attempts(5, 3, Duration.ofMinutes(10));
        attempts.failed(1, NOW);
        attempts.failed(2, NOW);
        attempts.failed(3, NOW.plusSeconds(1));

        assertThat(attempts.blocked(4, NOW.plusSeconds(2))).isTrue();
        assertThat(attempts.blocked(4, NOW.plus(Duration.ofMinutes(10)))).isFalse();
    }
}
