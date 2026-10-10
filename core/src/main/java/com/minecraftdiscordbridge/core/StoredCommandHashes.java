package com.minecraftdiscordbridge.core;

import com.minecraftdiscordbridge.core.discord.CommandHashRepository;
import com.minecraftdiscordbridge.core.loop.IoExecutor;
import com.minecraftdiscordbridge.core.storage.CommandHashStore;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

final class StoredCommandHashes implements CommandHashRepository {
    private final CommandHashStore store;
    private final IoExecutor io;

    StoredCommandHashes(CommandHashStore store, IoExecutor io) {
        this.store = store;
        this.io = io;
    }

    @Override
    public CompletionStage<Optional<String>> find(long guildId) {
        return io.submit(() -> store.hash(Long.toUnsignedString(guildId)));
    }

    @Override
    public CompletionStage<Void> save(long guildId, String hash) {
        return io.perform(() -> store.put(Long.toUnsignedString(guildId), hash)).thenAccept(done -> {});
    }
}
