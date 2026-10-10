package com.minecraftdiscordbridge.core.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.minecraftdiscordbridge.core.discord.DiscordId;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BindingStoreTest {
    private static final ChannelBinding BINDING = new ChannelBinding(
            new DiscordId(100000000000000000L),
            Map.of(
                    ChannelRole.CHAT,
                    new DiscordId(100000000000000001L),
                    ChannelRole.EVENTS,
                    new DiscordId(100000000000000002L)));

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
    }

    @Test
    void keepsAFileWithAChannelThatIsNotADiscordIdUntouched() throws IOException {
        Path file = directory.resolve("data").resolve("channels.json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, """
                {"version": 1, "guildId": "100000000000000000", "channels": {"chat": "general"}}
                """);

        BindingStore store = BindingStore.open(directory);

        assertThat(store.current()).isEmpty();
        assertThat(store.readOnly()).isTrue();
    }
}
