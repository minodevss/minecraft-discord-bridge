package com.minecraftdiscordbridge.core.config;

import java.util.List;

public sealed interface ConfigResult permits ConfigResult.Loaded, ConfigResult.Failed {
    List<ConfigProblem> warnings();

    record Loaded(ConfigSnapshot snapshot, List<ConfigProblem> warnings) implements ConfigResult {
        public Loaded {
            warnings = List.copyOf(warnings);
        }
    }

    record Failed(List<ConfigProblem> errors, List<ConfigProblem> warnings) implements ConfigResult {
        public Failed {
            errors = List.copyOf(errors);
            warnings = List.copyOf(warnings);
        }
    }
}
