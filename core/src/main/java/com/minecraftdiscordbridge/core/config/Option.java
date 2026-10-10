package com.minecraftdiscordbridge.core.config;

import java.util.List;

record Option<T>(String path, ValueType<T> type, T defaultValue, List<String> comment) implements Block {
    Option {
        comment = List.copyOf(comment);
    }

    String key() {
        return path.substring(path.lastIndexOf('.') + 1);
    }
}
