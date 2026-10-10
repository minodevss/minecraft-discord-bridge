package com.minecraftdiscordbridge.core.config;

import java.util.Collection;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

final class Suggestions {
    private Suggestions() {}

    static String unknownKey(String key, Collection<String> known) {
        return closest(key, known)
                .map(match -> "unknown key is ignored; did you mean " + match + "?")
                .orElse("unknown key is ignored");
    }

    static Optional<String> closest(String key, Collection<String> candidates) {
        @Nullable String best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (String candidate : candidates) {
            int distance = distance(key, candidate);
            if (distance < bestDistance) {
                best = candidate;
                bestDistance = distance;
            }
        }
        if (best != null && bestDistance <= Math.max(2, key.length() / 3)) {
            return Optional.of(best);
        }
        String leaf = leaf(key);
        return candidates.stream()
                .filter(candidate -> leaf(candidate).equals(leaf))
                .findFirst();
    }

    private static String leaf(String path) {
        return path.substring(path.lastIndexOf('.') + 1);
    }

    private static int distance(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int substitution = previous[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1);
                current[j] = Math.min(substitution, Math.min(previous[j], current[j - 1]) + 1);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }
}
