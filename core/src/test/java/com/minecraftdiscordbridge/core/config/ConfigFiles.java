package com.minecraftdiscordbridge.core.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

final class ConfigFiles {
    private ConfigFiles() {}

    static ConfigResult load(Path directory, String config) throws IOException {
        Files.writeString(directory.resolve("config.yml"), config, StandardCharsets.UTF_8);
        return new ConfigLoader(directory, Map.of()).load();
    }

    static ConfigSnapshot loaded(ConfigResult result) {
        if (result instanceof ConfigResult.Loaded loaded) {
            return loaded.snapshot();
        }
        throw new AssertionError("expected the config to load, got " + result);
    }

    static List<String> errors(ConfigResult result) {
        if (result instanceof ConfigResult.Failed failed) {
            return failed.errors().stream().map(ConfigProblem::toString).toList();
        }
        throw new AssertionError("expected the config to fail, got " + result);
    }

    static List<String> warnings(ConfigResult result) {
        return result.warnings().stream().map(ConfigProblem::toString).toList();
    }

    static String resource(String name) throws IOException {
        try (var stream = ConfigFiles.class.getResourceAsStream("/config/" + name)) {
            if (stream == null) {
                throw new IOException("Missing test resource " + name);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
