package com.minecraftdiscordbridge.core.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BindingStoreTest {
    private static final ChannelBinding BINDING = new ChannelBinding(
            "100000000000000000",
            Map.of(ChannelRole.CHAT, "100000000000000001", ChannelRole.CONSOLE, "100000000000000003"));

    @TempDir
    Path directory;

    @Test
    void startsEmpty() {
        assertThat(BindingStore.open(directory).current()).isEmpty();
    }

    @Test
    void keepsTheSetupAcrossRestarts() throws IOException {
        BindingStore.open(directory).save(BINDING);

        BindingStore reopened = BindingStore.open(directory);

        assertThat(reopened.current()).contains(BINDING);
        assertThat(reopened.current().orElseThrow().channel(ChannelRole.EVENTS)).isEmpty();
    }

    @Test
    void rejectsChannelsThatAreNotDiscordIds() {
        assertThatThrownBy(() -> new ChannelBinding("100000000000000000", Map.of(ChannelRole.CHAT, "general")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chat channel");
    }
}
