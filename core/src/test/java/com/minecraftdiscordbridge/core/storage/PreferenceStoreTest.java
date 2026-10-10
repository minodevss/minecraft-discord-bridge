package com.minecraftdiscordbridge.core.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PreferenceStoreTest {
    private static final UUID STEVE = UUID.fromString("00000000-0000-4000-8000-000000000001");

    @TempDir
    Path directory;

    @Test
    void remembersWhoHidDiscordMessagesAcrossRestarts() throws IOException {
        PreferenceStore store = PreferenceStore.open(directory);

        store.hideDiscord(STEVE, true);

        assertThat(PreferenceStore.open(directory).hidingDiscord()).containsExactly(STEVE);
        store.hideDiscord(STEVE, false);
        assertThat(PreferenceStore.open(directory).hidingDiscord()).isEmpty();
    }

    @Test
    void becomesReadOnlyInsteadOfOverwritingADamagedFile() throws IOException {
        Path file = directory.resolve("data").resolve("preferences.json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "{\"version\":1,\"hideDiscordMessages\":[\"not-a-uuid\"]}");

        PreferenceStore store = PreferenceStore.open(directory);

        assertThat(store.readOnly()).isTrue();
        assertThat(store.hidingDiscord()).isEmpty();
        assertThatThrownBy(() -> store.hideDiscord(STEVE, true)).isInstanceOf(ReadOnlyStoreException.class);
        assertThat(Files.readString(file)).contains("not-a-uuid");
    }
}
