package com.minecraftdiscordbridge.core.discord;

import java.util.List;
import java.util.Optional;

public record CommandSpec(
        String name,
        String description,
        Optional<Localized> korean,
        List<Option> options,
        List<Subcommand> subcommands,
        boolean adminOnly,
        boolean slow,
        boolean ephemeral) {
    public CommandSpec {
        options = List.copyOf(options);
        subcommands = List.copyOf(subcommands);
        if (!options.isEmpty() && !subcommands.isEmpty()) {
            throw new IllegalArgumentException("Command /" + name + " cannot have both options and subcommands");
        }
    }

    public CommandSpec(
            String name,
            String description,
            Optional<Localized> korean,
            List<Option> options,
            boolean adminOnly,
            boolean slow,
            boolean ephemeral) {
        this(name, description, korean, options, List.of(), adminOnly, slow, ephemeral);
    }

    public record Localized(String name, String description) {}

    public record Option(
            OptionType type,
            String name,
            String description,
            Optional<Localized> korean,
            boolean required,
            boolean autocomplete) {}

    public record Subcommand(String name, String description, Optional<Localized> korean, List<Option> options) {
        public Subcommand {
            options = List.copyOf(options);
        }
    }

    public enum OptionType {
        STRING,
        INTEGER,
        BOOLEAN,
        USER
    }
}
