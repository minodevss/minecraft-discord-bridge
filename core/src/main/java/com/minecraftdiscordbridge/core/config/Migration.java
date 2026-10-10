package com.minecraftdiscordbridge.core.config;

import java.util.List;

record Migration(int from, List<MigrationStep> steps) {
    Migration {
        steps = List.copyOf(steps);
    }

    void apply(RawConfig config) {
        steps.forEach(step -> step.apply(config));
    }
}
