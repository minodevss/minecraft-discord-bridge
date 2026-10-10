package com.minecraftdiscordbridge.core.storage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

public final class CommandHashStore {
    private final StoreFile file;
    private volatile Map<String, String> byGuild;

    private CommandHashStore(StoreFile file) {
        this.file = file;
        this.byGuild = file.load(CommandHashesJson.class, json -> Map.copyOf(json.guilds()), Map.of());
    }

    public static CommandHashStore open(Path dataDirectory) {
        return new CommandHashStore(StoreFile.plain(dataDirectory, "command-hashes.json", "Slash command hashes"));
    }

    public boolean readOnly() {
        return file.readOnly();
    }

    public Optional<String> hash(String guildId) {
        return Optional.ofNullable(byGuild.get(guildId));
    }

    public synchronized void put(String guildId, String hash) throws IOException {
        if (guildId.isBlank()) {
            throw new IllegalArgumentException("guildId must not be blank");
        }
        Map<String, String> next = new TreeMap<>(byGuild);
        next.put(guildId, hash);
        file.save(new CommandHashesJson(StoreFile.FORMAT, next));
        byGuild = Map.copyOf(next);
    }

    record CommandHashesJson(int version, Map<String, String> guilds) implements Versioned {}
}
