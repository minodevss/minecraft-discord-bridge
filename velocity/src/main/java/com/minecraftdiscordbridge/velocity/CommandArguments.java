package com.minecraftdiscordbridge.velocity;

import java.util.List;

final class CommandArguments {
    private CommandArguments() {}

    static List<String> split(String input) {
        String trimmed = input.strip();
        return trimmed.isEmpty() ? List.of() : List.of(trimmed.split("\\s+"));
    }
}
