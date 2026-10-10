package com.minecraftdiscordbridge.core;

import com.minecraftdiscordbridge.core.storage.BindingStore;
import com.minecraftdiscordbridge.core.storage.CommandHashStore;
import com.minecraftdiscordbridge.core.storage.LinkStore;
import com.minecraftdiscordbridge.core.storage.PreferenceStore;
import com.minecraftdiscordbridge.core.storage.WebhookStore;
import java.nio.file.Path;

record Stores(
        LinkStore links,
        BindingStore bindings,
        WebhookStore webhooks,
        CommandHashStore commandHashes,
        PreferenceStore preferences) {
    static Stores open(Path dataDirectory) {
        return new Stores(
                LinkStore.open(dataDirectory),
                BindingStore.open(dataDirectory),
                WebhookStore.open(dataDirectory),
                CommandHashStore.open(dataDirectory),
                PreferenceStore.open(dataDirectory));
    }
}
