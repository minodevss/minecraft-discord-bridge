package com.minecraftdiscordbridge.core.text;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.flattener.ComponentFlattener;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.json.JSONOptions;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public final class TextRenderer {
    private static final Map<Integer, GsonComponentSerializer> WRITERS = new ConcurrentHashMap<>();
    private static final GsonComponentSerializer READER = GsonComponentSerializer.builder()
            .options(JSONOptions.compatibility())
            .build();

    private TextRenderer() {}

    public static String toJson(Component component, int dataVersion) {
        return WRITERS.computeIfAbsent(dataVersion, TextRenderer::writer).serialize(component);
    }

    public static Component fromJson(String json) {
        try {
            return READER.deserialize(json);
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException(
                    "Not a valid Minecraft text component: " + invalid.getMessage(), invalid);
        }
    }

    public static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    public static String plain(Component component, Translations translations) {
        ComponentFlattener flattener = ComponentFlattener.basic().toBuilder()
                .complexMapper(
                        TranslatableComponent.class,
                        (translatable, parts) -> TranslationFormat.flatten(translatable, translations, parts))
                .build();
        return PlainTextComponentSerializer.builder()
                .flattener(flattener)
                .build()
                .serialize(component);
    }

    private static GsonComponentSerializer writer(int dataVersion) {
        return GsonComponentSerializer.builder()
                .options(JSONOptions.byDataVersion().at(dataVersion))
                .build();
    }
}
