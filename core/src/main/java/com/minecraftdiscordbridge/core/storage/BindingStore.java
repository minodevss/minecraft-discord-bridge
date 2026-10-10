package com.minecraftdiscordbridge.core.storage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

public final class BindingStore {
    private final StoreFile file;
    private volatile Optional<ChannelBinding> current;

    private BindingStore(StoreFile file) {
        this.file = file;
        this.current = file.load(BindingJson.class, json -> Optional.of(decode(json)), Optional.empty());
    }

    public static BindingStore open(Path dataDirectory) {
        return new BindingStore(StoreFile.withBackups(dataDirectory, "channels.json", "Channel settings from /setup"));
    }

    public boolean readOnly() {
        return file.readOnly();
    }

    public Optional<ChannelBinding> current() {
        return current;
    }

    public synchronized void save(ChannelBinding binding) throws IOException {
        file.save(encode(binding));
        current = Optional.of(binding);
    }

    private static ChannelBinding decode(BindingJson json) {
        Map<ChannelRole, String> channels = new EnumMap<>(ChannelRole.class);
        json.channels().forEach((role, channel) -> channels.put(ChannelRole.fromId(role), channel));
        return new ChannelBinding(json.guildId(), channels);
    }

    private static BindingJson encode(ChannelBinding binding) {
        Map<String, String> channels = new TreeMap<>();
        binding.channels().forEach((role, channel) -> channels.put(role.id(), channel));
        return new BindingJson(StoreFile.FORMAT, binding.guildId(), channels);
    }

    record BindingJson(int version, String guildId, Map<String, String> channels) implements Versioned {}
}
