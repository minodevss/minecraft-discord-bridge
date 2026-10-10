package com.minecraftdiscordbridge.core.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.minecraftdiscordbridge.core.discord.DiscordId;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WebhookStoreTest {
    private static final DiscordId CHANNEL = new DiscordId(100000000000000001L);
    private static final Webhook WEBHOOK = new Webhook(new DiscordId(200000000000000001L), "very-secret-token");

    @TempDir
    Path directory;

    @Test
    void keepsWebhooksAcrossRestarts() throws IOException {
        WebhookStore.open(directory).put(CHANNEL, WEBHOOK);

        WebhookStore reopened = WebhookStore.open(directory);

        assertThat(reopened.forChannel(CHANNEL)).contains(WEBHOOK);
        assertThat(reopened.forChannel(new DiscordId(100000000000000002L))).isEmpty();
    }

    @Test
    void removesAWebhook() throws IOException {
        WebhookStore store = WebhookStore.open(directory);
        store.put(CHANNEL, WEBHOOK);

        store.remove(CHANNEL);

        assertThat(WebhookStore.open(directory).forChannel(CHANNEL)).isEmpty();
    }

    @Test
    void writesTheFileOwnerOnly() throws IOException {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));

        WebhookStore.open(directory).put(CHANNEL, WEBHOOK);

        Path file = directory.resolve("data").resolve("webhooks.json");
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(file)))
                .isEqualTo("rw-------");
    }

    @Test
    void hidesTheTokenInToString() {
        assertThat(WEBHOOK.toString()).doesNotContain("very-secret-token").contains("200000000000000001");
    }
}
