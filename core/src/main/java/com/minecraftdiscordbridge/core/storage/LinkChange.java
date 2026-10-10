package com.minecraftdiscordbridge.core.storage;

public sealed interface LinkChange {
    Link link();

    record Linked(Link link) implements LinkChange {}

    record Unlinked(Link link) implements LinkChange {}
}
