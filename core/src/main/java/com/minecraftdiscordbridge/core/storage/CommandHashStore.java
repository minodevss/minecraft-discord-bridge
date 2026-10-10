package com.minecraftdiscordbridge.core.storage;

import com.minecraftdiscordbridge.core.discord.DiscordId;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

public final class CommandHashStore {
    private final StoreFile file;
    private volatile Map<DiscordId, String> byGuild;

    private CommandHashStore(StoreFile file) {
        this.file = file;
        this.byGuild = file.load(CommandHashesJson.class, CommandHashStore::decode, Map.of());
    }

    public static CommandHashStore open(Path dataDirectory) {
        return new CommandHashStore(StoreFile.plain(dataDirectory, "command-hashes.json", "Slash command hashes"));
    }

    public boolean readOnly() {
        return file.readOnly();
    }

    public Optional<String> hash(DiscordId guildId) {
        return Optional.ofNullable(byGuild.get(guildId));
    }

    public synchronized void put(DiscordId guildId, String hash) throws IOException {
        Map<DiscordId, String> next = new HashMap<>(byGuild);
        next.put(guildId, hash);
        Map<String, String> guilds = new TreeMap<>();
        next.forEach((guild, guildHash) -> guilds.put(guild.toString(), guildHash));
        file.save(new CommandHashesJson(StoreFile.FORMAT, guilds));
        byGuild = Map.copyOf(next);
    }

    private static Map<DiscordId, String> decode(CommandHashesJson json) {
        Map<DiscordId, String> byGuild = new HashMap<>();
        json.guilds().forEach((guild, hash) -> byGuild.put(DiscordId.require(guild, "guild id"), hash));
        return Map.copyOf(byGuild);
    }

    record CommandHashesJson(int version, Map<String, String> guilds) implements Versioned {}
}
