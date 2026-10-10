package com.minecraftdiscordbridge.core.text;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;

final class ComponentBuffer {
    private final List<Component> parts = new ArrayList<>();
    private final StringBuilder pending = new StringBuilder();

    void text(char c) {
        pending.append(c);
    }

    void text(String text) {
        pending.append(text);
    }

    void text(String text, int start, int end) {
        pending.append(text, start, end);
    }

    void add(Component component) {
        flush();
        parts.add(component);
    }

    void addAll(List<Component> components) {
        flush();
        parts.addAll(components);
    }

    List<Component> finish() {
        flush();
        return parts;
    }

    private void flush() {
        if (pending.length() > 0) {
            parts.add(Component.text(pending.toString()));
            pending.setLength(0);
        }
    }
}
