package com.minecraftdiscordbridge.core.config;

import com.minecraftdiscordbridge.core.config.YamlValue.Field;
import com.minecraftdiscordbridge.core.config.YamlValue.Mapping;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;

final class RawConfig {
    private final Map<String, Entry> entries = new LinkedHashMap<>();

    private RawConfig() {}

    static Optional<RawConfig> parse(String text, Set<String> leaves, Problems problems) {
        return YamlReader.readDocument(text, problems).map(document -> {
            RawConfig raw = new RawConfig();
            raw.add(document, "", leaves, problems);
            return raw;
        });
    }

    private void add(Mapping mapping, String prefix, Set<String> leaves, Problems problems) {
        for (Field field : mapping.fields()) {
            String path = prefix.isEmpty() ? field.key() : prefix + "." + field.key();
            if (field.value() instanceof Mapping nested && !nested.fields().isEmpty() && !leaves.contains(path)) {
                add(nested, path, leaves, problems);
                continue;
            }
            Entry first = entries.get(path);
            if (first != null) {
                problems.error(field.keyAt(), path, YamlReader.duplicate(first.keyAt()));
                continue;
            }
            entries.put(path, new Entry(field.keyAt(), field.value()));
        }
    }

    Optional<Entry> get(String path) {
        return Optional.ofNullable(entries.get(path));
    }

    List<String> paths() {
        return List.copyOf(entries.keySet());
    }

    void move(String from, String to) {
        for (String path : paths()) {
            if (within(path, from)) {
                Entry entry = entries.remove(path);
                if (entry != null) {
                    entries.putIfAbsent(to + path.substring(from.length()), entry);
                }
            }
        }
    }

    void delete(String path) {
        entries.keySet().removeIf(existing -> within(existing, path));
    }

    void mapValue(String path, UnaryOperator<YamlValue> change) {
        entries.computeIfPresent(path, (unused, entry) -> new Entry(entry.keyAt(), change.apply(entry.value())));
    }

    private static boolean within(String path, String root) {
        return path.equals(root) || path.startsWith(root + ".");
    }

    record Entry(Location keyAt, YamlValue value) {}
}
