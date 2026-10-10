package com.minecraftdiscordbridge.core.log;

import java.nio.file.FileSystemException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

public final class Failures {
    private Failures() {}

    public static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    public static String describe(Throwable failure) {
        Throwable cause = unwrap(failure);
        String message = cause.getMessage();
        if (message == null || message.isBlank()) {
            return cause.getClass().getSimpleName();
        }
        // A FileSystemException message is only the path; its type says what went wrong.
        return cause instanceof FileSystemException ? cause.getClass().getSimpleName() + ": " + message : message;
    }
}
