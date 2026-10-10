package com.minecraftdiscordbridge.core.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CommandHashStoreTest {
    private static final String GUILD = "100000000000000000";

    @TempDir
    Path directory;

    @Test
    void remembersTheLastHashPerGuildAcrossRestarts() throws IOException {
        CommandHashStore store = CommandHashStore.open(directory);
        store.put(GUILD, "first");
        store.put(GUILD, "second");

        CommandHashStore reopened = CommandHashStore.open(directory);

        assertThat(reopened.hash(GUILD)).contains("second");
        assertThat(reopened.hash("100000000000000001")).isEmpty();
    }
}
