package com.minecraftdiscordbridge.core.discord;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

final class TestSupport {
    private TestSupport() {}

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
