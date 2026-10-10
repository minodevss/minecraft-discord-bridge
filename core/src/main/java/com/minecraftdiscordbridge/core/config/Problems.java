package com.minecraftdiscordbridge.core.config;

import com.minecraftdiscordbridge.core.config.ConfigProblem.Severity;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

final class Problems {
    private final String source;
    private final List<ConfigProblem> all;

    Problems(String source) {
        this(source, new ArrayList<>());
    }

    private Problems(String source, List<ConfigProblem> all) {
        this.source = source;
        this.all = all;
    }

    Problems forSource(String other) {
        return new Problems(other, all);
    }

    void error(@Nullable Location at, String path, String message) {
        all.add(new ConfigProblem(Severity.ERROR, source, at, path, message));
    }

    void warning(@Nullable Location at, String path, String message) {
        all.add(new ConfigProblem(Severity.WARNING, source, at, path, message));
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
