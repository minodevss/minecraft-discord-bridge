package com.minecraftdiscordbridge.core.config;

import java.util.function.UnaryOperator;

@FunctionalInterface
interface MigrationStep {
    void apply(RawConfig config);

    static MigrationStep rename(String path, String newKey) {
        int dot = path.lastIndexOf('.');
        return move(path, dot < 0 ? newKey : path.substring(0, dot + 1) + newKey);
    }

    static MigrationStep move(String from, String to) {
        return config -> config.move(from, to);
    }

    static MigrationStep mapValue(String path, UnaryOperator<YamlValue> change) {
        return config -> config.mapValue(path, change);
    }

    static MigrationStep delete(String path) {
        return config -> config.delete(path);
    }
}
