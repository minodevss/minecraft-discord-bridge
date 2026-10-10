package com.minecraftdiscordbridge.core.discord;

import java.util.List;
import java.util.Optional;

public record CommandSpec(
        String name,
        String description,
        Optional<String> koreanDescription,
        List<Option> options,
        List<Subcommand> subcommands,
        Access access,
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
            Optional<String> koreanDescription,
            List<Option> options,
            Access access,
            boolean slow,
            boolean ephemeral) {
        this(name, description, koreanDescription, options, List.of(), access, slow, ephemeral);
    }

    public record Option(
            OptionType type,
            String name,
            String description,
            Optional<String> koreanDescription,
            boolean required,
            boolean autocomplete) {}

    public record Subcommand(
            String name, String description, Optional<String> koreanDescription, List<Option> options) {
        public Subcommand {
            options = List.copyOf(options);
        }
    }

    public enum Access {
        EVERYONE,
        MANAGE_SERVER,
        ADMINS
    }

    public enum OptionType {
        STRING,
        INTEGER,
        BOOLEAN,
        USER
    }
}
