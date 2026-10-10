package com.minecraftdiscordbridge.core.storage;

import com.minecraftdiscordbridge.core.discord.CommandHashRepository;
import com.minecraftdiscordbridge.core.loop.IoExecutor;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

public final class StoredCommandHashes implements CommandHashRepository {
    private final CommandHashStore store;
    private final IoExecutor io;

    public StoredCommandHashes(CommandHashStore store, IoExecutor io) {
        this.store = store;
        this.io = io;
    }

    @Override
    public CompletionStage<Optional<String>> find(long guildId) {
        return io.completeOnIo(() -> store.hash(Long.toUnsignedString(guildId)));
    }

    @Override
    public CompletionStage<Void> save(long guildId, String hash) {
        return io.completeOnIo(() -> store.put(Long.toUnsignedString(guildId), hash));
    }
}
