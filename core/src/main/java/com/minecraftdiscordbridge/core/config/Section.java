package com.minecraftdiscordbridge.core.config;

import java.util.List;

record Section(String name, List<String> comment, List<Option<?>> options) implements Block {
    Section {
        comment = List.copyOf(comment);
        options = List.copyOf(options);
    }
}
