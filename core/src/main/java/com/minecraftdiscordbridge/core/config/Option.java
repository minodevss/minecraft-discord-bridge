package com.minecraftdiscordbridge.core.config;

import java.util.List;

record Option<T>(String path, ValueType<T> type, T defaultValue, List<String> comment, int since) {
    Option {
        comment = List.copyOf(comment);
    }

    String key() {
        return path.substring(path.lastIndexOf('.') + 1);
    }

    String section() {
        int dot = path.indexOf('.');
        return dot < 0 ? "" : path.substring(0, dot);
    }
}
