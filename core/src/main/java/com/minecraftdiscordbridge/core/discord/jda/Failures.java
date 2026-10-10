package com.minecraftdiscordbridge.core.discord.jda;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

final class Failures {
    private Failures() {}

    static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    static String describe(Throwable failure) {
        Throwable cause = unwrap(failure);
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }
}
