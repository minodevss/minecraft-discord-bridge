package com.minecraftdiscordbridge.core.storage;

import java.nio.file.Path;

public record Stores(
        LinkStore links,
        BindingStore bindings,
        WebhookStore webhooks,
        CommandHashStore commandHashes,
        PreferenceStore preferences) {
    public static Stores open(Path dataDirectory) {
        return new Stores(
                LinkStore.open(dataDirectory),
                BindingStore.open(dataDirectory),
                WebhookStore.open(dataDirectory),
                CommandHashStore.open(dataDirectory),
                PreferenceStore.open(dataDirectory));
    }
}
