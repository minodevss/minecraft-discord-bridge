package com.minecraftdiscordbridge.core.vanilla;

import static com.minecraftdiscordbridge.core.log.Log.LOGGER;

import com.minecraftdiscordbridge.core.i18n.Language;
import com.minecraftdiscordbridge.core.log.Failures;
import com.minecraftdiscordbridge.core.loop.IoExecutor;
import com.minecraftdiscordbridge.core.text.Translations;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public final class VanillaTranslations implements Translations {
    private final LanguageFiles files;
    private final IoExecutor io;
    private final String minecraftVersion;
    private volatile Map<String, String> patterns = Map.of();
    private @Nullable String requested;

    public VanillaTranslations(LanguageFiles files, IoExecutor io, String minecraftVersion) {
        this.files = files;
        this.io = io;
        this.minecraftVersion = minecraftVersion;
    }

    public static Optional<String> locale(Language language) {
        return switch (language) {
            case EN -> Optional.empty();
            case KO -> Optional.of("ko_kr");
        };
    }

    public Optional<Translations> use(Language language) {
        Optional<String> wanted = locale(language);
        if (wanted.isEmpty()) {
            requested = null;
            patterns = Map.of();
            return Optional.empty();
        }
        String locale = wanted.get();
        if (!locale.equals(requested)) {
            requested = locale;
            patterns = Map.of();
            io.completeOnLoop(
                    () -> files.load(minecraftVersion, locale),
                    loaded -> loaded(locale, loaded),
                    failure -> failed(locale, failure));
        }
        return Optional.of(this);
    }

    @Override
    public Optional<String> pattern(String key) {
        return Optional.ofNullable(patterns.get(key));
    }

    public void close() {
        files.close();
    }

    private void loaded(String locale, Map<String, String> loaded) {
        if (locale.equals(requested)) {
            patterns = loaded;
        }
    }

    private void failed(String locale, Throwable failure) {
        if (!locale.equals(requested)) {
            return;
        }
        requested = null;
        LOGGER.warn(
                "Could not load Minecraft {}'s {} language file: {}. Death messages and advancements are posted in"
                        + " English until the server can reach piston-meta.mojang.com and"
                        + " resources.download.minecraft.net and you run /discord reload",
                minecraftVersion,
                locale,
                Failures.describe(failure));
    }
}
