package com.minecraftdiscordbridge.core.access;

public sealed interface GroupCheck {
    record Safe() implements GroupCheck {}

    record Missing() implements GroupCheck {}

    record Dangerous(String node) implements GroupCheck {}
}
