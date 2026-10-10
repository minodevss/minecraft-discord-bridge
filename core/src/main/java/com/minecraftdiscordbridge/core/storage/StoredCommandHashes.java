package com.minecraftdiscordbridge.core.storage;

import com.minecraftdiscordbridge.core.discord.CommandHashRepository;
import com.minecraftdiscordbridge.core.discord.DiscordId;
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
    public CompletionStage<Optional<String>> find(DiscordId guildId) {
        return io.completeOnIo(() -> store.hash(guildId));
    }

    @Override
    public CompletionStage<Void> save(DiscordId guildId, String hash) {
        return io.completeOnIo(() -> store.put(guildId, hash));
    }
}
