package com.minecraftdiscordbridge.core.config;

public record Location(int line, int column) {
    @Override
    public String toString() {
        return line + ":" + column;
    }
}
