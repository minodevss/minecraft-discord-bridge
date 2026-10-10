package com.minecraftdiscordbridge.core.discord;

import java.util.List;
import org.jspecify.annotations.Nullable;

public record CommandSpec(
        String name,
        String description,
        @Nullable Localized korean,
        List<Option> options,
        boolean adminOnly,
        boolean slow,
        boolean ephemeral) {
    public CommandSpec {
        options = List.copyOf(options);
    }

    public record Localized(String name, String description) {}

    public record Option(
            OptionType type,
            String name,
            String description,
            @Nullable Localized korean,
            boolean required,
            boolean autocomplete) {}

    public enum OptionType {
        STRING,
        INTEGER,
        BOOLEAN,
        USER
    }
}
