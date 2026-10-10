package com.minecraftdiscordbridge.core.config;

import org.jspecify.annotations.Nullable;

interface ValueType<T> {
    @Nullable
    T read(YamlValue value, String path, Problems problems);

    void write(StringBuilder out, String indent, String key, T value);

    void writeItem(StringBuilder out, String indent, T value);
}
