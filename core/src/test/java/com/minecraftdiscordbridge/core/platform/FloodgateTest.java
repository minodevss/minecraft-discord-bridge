package com.minecraftdiscordbridge.core.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class FloodgateTest {
    private static final UUID BEDROCK = UUID.fromString("00000000-0000-0000-0009-01f64f65c7c3");
    private static final UUID JAVA = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");

    @Test
    void asksFloodgateWhichPlayersAreOnBedrock() {
        Predicate<UUID> bedrock = Floodgate.bedrockCheck(FakeApi.class.getName());

        assertThat(bedrock.test(BEDROCK)).isTrue();
        assertThat(bedrock.test(JAVA)).isFalse();
    }

    @Test
    void treatsEveryoneAsJavaWithoutFloodgate() {
        assertThat(Floodgate.bedrockCheck().test(BEDROCK)).isFalse();
        assertThat(Floodgate.bedrockCheck("org.example.NoSuchApi").test(BEDROCK))
                .isFalse();
    }

    @Test
    void treatsEveryoneAsJavaBeforeFloodgateIsReady() {
        assertThat(Floodgate.bedrockCheck(UnreadyApi.class.getName()).test(BEDROCK))
                .isFalse();
    }

    public static final class FakeApi {
        private static final FakeApi INSTANCE = new FakeApi();

        public static FakeApi getInstance() {
            return INSTANCE;
        }

        public boolean isFloodgatePlayer(UUID id) {
            return BEDROCK.equals(id);
        }
    }

    public static final class UnreadyApi {
        public static @Nullable UnreadyApi getInstance() {
            return null;
        }

        public boolean isFloodgatePlayer(UUID id) {
            return true;
        }
    }
}
