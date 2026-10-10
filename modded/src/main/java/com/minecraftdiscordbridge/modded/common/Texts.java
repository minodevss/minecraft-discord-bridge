package com.minecraftdiscordbridge.modded.common;

import com.minecraftdiscordbridge.core.platform.RichText;
import java.util.Optional;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
//? if >=1.21 {
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.network.chat.ComponentSerialization;
//?}

final class Texts {
    private static final RateLimitedWarning UNREADABLE = new RateLimitedWarning();

    private Texts() {}

    static Optional<Component> parse(String json, RegistryAccess registries) {
        try {
            return Optional.of(fromJson(json, registries));
        } catch (RuntimeException error) {
            UNREADABLE.warn(
                    "Skipped a message whose text JSON this Minecraft version cannot read;"
                            + " update Minecraft Discord Bridge if this repeats",
                    error);
            return Optional.empty();
        }
    }

    static RichText richText(Component component, RegistryAccess registries) {
        return new RichText(toJson(component, registries), component.getString());
    }

    //? if >=1.21 {
    private static Component fromJson(String json, RegistryAccess registries) {
        return ComponentSerialization.CODEC
                .parse(registries.createSerializationContext(JsonOps.INSTANCE), JsonParser.parseString(json))
                .getOrThrow();
    }

    private static String toJson(Component component, RegistryAccess registries) {
        return ComponentSerialization.CODEC
                .encodeStart(registries.createSerializationContext(JsonOps.INSTANCE), component)
                .getOrThrow()
                .toString();
    }
    //?} else {
    /*private static Component fromJson(String json, RegistryAccess registries) {
        Component component = Component.Serializer.fromJson(json);
        if (component == null) {
            throw new IllegalArgumentException("Text JSON is null");
        }
        return component;
    }

    private static String toJson(Component component, RegistryAccess registries) {
        return Component.Serializer.toJson(component);
    }
    *///?}
}
