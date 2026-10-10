package com.minecraftdiscordbridge.core.storage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class PreferenceStore {
    private final StoreFile file;
    private volatile Set<UUID> hidingDiscord;

    private PreferenceStore(StoreFile file) {
        this.file = file;
        this.hidingDiscord = file.load(PreferencesJson.class, PreferenceStore::decode, Set.of());
    }

    public static PreferenceStore open(Path dataDirectory) {
        return new PreferenceStore(StoreFile.plain(dataDirectory, "preferences.json", "Player preferences"));
    }

    public boolean readOnly() {
        return file.readOnly();
    }

    public Set<UUID> hidingDiscord() {
        return hidingDiscord;
    }

    public synchronized void hideDiscord(UUID player, boolean hidden) throws IOException {
        Set<UUID> next = new HashSet<>(hidingDiscord);
        boolean changed = hidden ? next.add(player) : next.remove(player);
        if (!changed) {
            return;
        }
        List<String> ids = next.stream().map(UUID::toString).sorted().toList();
        file.save(new PreferencesJson(StoreFile.FORMAT, ids));
        hidingDiscord = Set.copyOf(next);
    }

    private static Set<UUID> decode(PreferencesJson json) {
        Set<UUID> ids = new HashSet<>();
        json.hideDiscordMessages().forEach(id -> ids.add(UUID.fromString(id)));
        return Set.copyOf(ids);
    }

    record PreferencesJson(int version, List<String> hideDiscordMessages) implements Versioned {}
}
