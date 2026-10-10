package com.minecraftdiscordbridge.core.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.minecraftdiscordbridge.core.discord.DiscordId;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CommandHashStoreTest {
    private static final DiscordId GUILD = new DiscordId(100000000000000000L);

    @TempDir
    Path directory;

    @Test
    void remembersTheLastHashPerGuildAcrossRestarts() throws IOException {
        CommandHashStore store = CommandHashStore.open(directory);
        store.put(GUILD, "first");
        store.put(GUILD, "second");

        CommandHashStore reopened = CommandHashStore.open(directory);

        assertThat(reopened.hash(GUILD)).contains("second");
        assertThat(reopened.hash(new DiscordId(100000000000000001L))).isEmpty();
    }
}
