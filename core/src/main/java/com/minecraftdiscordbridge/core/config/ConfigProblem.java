package com.minecraftdiscordbridge.core.config;

import org.jspecify.annotations.Nullable;

public record ConfigProblem(
        Severity severity, String source, @Nullable Location location, String path, String message) {
    public enum Severity {
        ERROR,
        WARNING
    }

    @Override
    public String toString() {
        StringBuilder text = new StringBuilder(source);
        if (location != null) {
            text.append(':').append(location);
        }
        if (!path.isEmpty()) {
            text.append(' ').append(path);
        }
        return text.append(": ").append(message).toString();
    }
}
