package com.minecraftdiscordbridge.core.config;

import com.minecraftdiscordbridge.core.config.ConfigProblem.Severity;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

final class Problems {
    private final String source;
    private final List<ConfigProblem> all;
    private final @Nullable String replacement;

    Problems(String source) {
        this(source, new ArrayList<>(), null);
    }

    private Problems(String source, List<ConfigProblem> all, @Nullable String replacement) {
        this.source = source;
        this.all = all;
        this.replacement = replacement;
    }

    Problems forSource(String other) {
        return new Problems(other, all, null);
    }

    Problems hidingDetails(String message) {
        return new Problems(source, all, message);
    }

    void error(@Nullable Location at, String path, String message) {
        add(Severity.ERROR, at, path, message);
    }

    void warning(@Nullable Location at, String path, String message) {
        add(Severity.WARNING, at, path, message);
    }

    private void add(Severity severity, @Nullable Location at, String path, String message) {
        String hidden = replacement;
        all.add(
                hidden == null
                        ? new ConfigProblem(severity, source, Optional.ofNullable(at), path, message)
                        : new ConfigProblem(severity, source, Optional.ofNullable(at), "", hidden));
    }

    boolean hasErrors() {
        return all.stream().anyMatch(problem -> problem.severity() == Severity.ERROR);
    }

    List<ConfigProblem> errors() {
        return only(Severity.ERROR);
    }

    List<ConfigProblem> warnings() {
        return only(Severity.WARNING);
    }

    private List<ConfigProblem> only(Severity severity) {
        return all.stream().filter(problem -> problem.severity() == severity).toList();
    }
}
