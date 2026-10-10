package com.minecraftdiscordbridge.core.config;

import java.util.List;

record Section(String name, List<String> comment) {
    Section {
        comment = List.copyOf(comment);
    }
}
