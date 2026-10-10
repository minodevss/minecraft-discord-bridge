package com.minecraftdiscordbridge.core.config;

import java.util.Optional;

public record ConfigProblem(
        Severity severity, String source, Optional<Location> location, String path, String message) {
    public enum Severity {
        ERROR,
        WARNING
    }

    @Override
    public String toString() {
        StringBuilder text = new StringBuilder(source);
        location.ifPresent(at -> text.append(':').append(at));
        if (!path.isEmpty()) {
            text.append(' ').append(path);
        }
        return text.append(": ").append(message).toString();
    }
}
