package com.minecraftdiscordbridge.core.discord;

import static org.assertj.core.api.Assertions.fail;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

final class TestSupport {
    private TestSupport() {}

    static DiscordSettings settings(List<CommandSpec> commands) {
        return new DiscordSettings(
                FakeDiscord.TOKEN,
                FakeDiscord.GUILD_ID,
                Set.of(FakeDiscord.CHANNEL_ID),
                Author.server("Survival", "https://example.com/server.png"),
                true,
                false,
                false,
                false,
                commands);
    }

    static void await(String what, BooleanSupplier condition) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                fail("Timed out waiting for " + what);
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                fail("Interrupted waiting for " + what);
            }
        }
    }

    static <T> T take(BlockingQueue<T> queue) {
        try {
            T value = queue.poll(10, TimeUnit.SECONDS);
            if (value == null) {
                fail("Nothing arrived");
            }
            return value;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

    static final class RecordingListener implements DiscordListener {
        final BlockingQueue<DiscordStatus> statuses = new LinkedBlockingQueue<>();
        final BlockingQueue<DiscordMessage> messages = new LinkedBlockingQueue<>();
        final BlockingQueue<InteractionRequest> commands = new LinkedBlockingQueue<>();
        final BlockingQueue<InteractionReply> replies = new LinkedBlockingQueue<>();
        final BlockingQueue<AutocompleteRequest> autocompletes = new LinkedBlockingQueue<>();
        final BlockingQueue<AutocompleteReply> autocompleteReplies = new LinkedBlockingQueue<>();

        @Override
        public void onStatusChanged(DiscordStatus status) {
            statuses.add(status);
        }

        @Override
        public void onMessage(DiscordMessage message) {
            messages.add(message);
        }

        @Override
        public void onCommand(InteractionRequest request, InteractionReply reply) {
            commands.add(request);
            replies.add(reply);
        }

        @Override
        public void onAutocomplete(AutocompleteRequest request, AutocompleteReply reply) {
            autocompletes.add(request);
            autocompleteReplies.add(reply);
        }
    }

    static final class MemoryWebhooks implements WebhookRepository {
        final Map<Long, WebhookCredentials> stored = new ConcurrentHashMap<>();

        @Override
        public CompletionStage<Optional<WebhookCredentials>> find(long channelId) {
            return CompletableFuture.completedFuture(Optional.ofNullable(stored.get(channelId)));
        }

        @Override
        public CompletionStage<Void> save(long channelId, WebhookCredentials webhook) {
            stored.put(channelId, webhook);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> forget(long channelId) {
            stored.remove(channelId);
            return CompletableFuture.completedFuture(null);
        }
    }

    static final class MemoryCommandHashes implements CommandHashRepository {
        final Map<Long, String> stored = new ConcurrentHashMap<>();

        @Override
        public CompletionStage<Optional<String>> find(long guildId) {
            return CompletableFuture.completedFuture(Optional.ofNullable(stored.get(guildId)));
        }

        @Override
        public CompletionStage<Void> save(long guildId, String hash) {
            stored.put(guildId, hash);
            return CompletableFuture.completedFuture(null);
        }
    }
}
