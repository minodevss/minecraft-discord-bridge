package com.minecraftdiscordbridge.core.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.minecraftdiscordbridge.core.discord.DiscordId;
import com.minecraftdiscordbridge.core.discord.WebhookCredentials;
import com.minecraftdiscordbridge.core.loop.IoExecutor;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StoredRepositoriesTest {
    private static final long CHANNEL = 222222222222222222L;
    private static final long GUILD = 111111111111111111L;
    private static final long UNSIGNED_WEBHOOK = -2L;

    @TempDir
    Path directory;

    private final IoExecutor io = new IoExecutor(Runnable::run);

    @AfterEach
    void closeIo() {
        io.close();
    }

    @Test
    void keepsWebhooksInTheStoreAcrossRestarts() throws Exception {
        StoredWebhooks webhooks = new StoredWebhooks(WebhookStore.open(directory), io);

        webhooks.save(CHANNEL, new WebhookCredentials(new DiscordId(UNSIGNED_WEBHOOK), "secret"))
                .toCompletableFuture()
                .get();

        StoredWebhooks reopened = new StoredWebhooks(WebhookStore.open(directory), io);
        assertThat(reopened.find(CHANNEL).toCompletableFuture().get())
                .contains(new WebhookCredentials(new DiscordId(UNSIGNED_WEBHOOK), "secret"));
        reopened.forget(CHANNEL).toCompletableFuture().get();
        assertThat(reopened.find(CHANNEL).toCompletableFuture().get()).isEmpty();
    }

    @Test
    void keepsCommandHashesPerGuild() throws Exception {
        StoredCommandHashes hashes = new StoredCommandHashes(CommandHashStore.open(directory), io);

        hashes.save(GUILD, "abc").toCompletableFuture().get();

        assertThat(new StoredCommandHashes(CommandHashStore.open(directory), io)
                        .find(GUILD)
                        .toCompletableFuture()
                        .get())
                .isEqualTo(Optional.of("abc"));
    }
}
