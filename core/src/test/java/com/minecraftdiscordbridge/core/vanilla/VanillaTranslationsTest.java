package com.minecraftdiscordbridge.core.vanilla;

import static org.assertj.core.api.Assertions.assertThat;

import com.minecraftdiscordbridge.core.fakes.Waits;
import com.minecraftdiscordbridge.core.i18n.Language;
import com.minecraftdiscordbridge.core.loop.IoExecutor;
import com.minecraftdiscordbridge.core.text.Translations;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VanillaTranslationsTest {
    private static final String KOREAN = "{\"death.attack.fall\":\"%1$s이(가) 땅바닥으로 곤두박질쳤습니다\"}";

    private final IoExecutor io = new IoExecutor(Runnable::run);
    private final FakeFetcher mojang = new FakeFetcher();

    @TempDir
    Path directory;

    @AfterEach
    void close() {
        io.close();
    }

    @Test
    void loadsMinecraftsKoreanInTheBackground() {
        mojang.serve("1.21.1", "ko_kr", KOREAN);

        Translations korean = translations().use(Language.KO).orElseThrow();

        Waits.until(
                "the Korean language file",
                () -> korean.pattern("death.attack.fall").isPresent());
        assertThat(korean.pattern("death.attack.fall")).contains("%1$s이(가) 땅바닥으로 곤두박질쳤습니다");
        assertThat(korean.pattern("death.attack.mymod.wisp")).isEmpty();
    }

    @Test
    void needsNoDownloadForEnglish() {
        assertThat(translations().use(Language.EN)).isEmpty();
        assertThat(mojang.requests).isEmpty();
    }

    @Test
    void staysEnglishAfterAFailedDownloadAndRetriesOnTheNextUse() {
        VanillaTranslations translations = translations();

        translations.use(Language.KO);
        io.completeOnIo(() -> {}).join();

        assertThat(translations.pattern("death.attack.fall")).isEmpty();
        mojang.serve("1.21.1", "ko_kr", KOREAN);
        translations.use(Language.KO);
        Waits.until(
                "the Korean language file",
                () -> translations.pattern("death.attack.fall").isPresent());
    }

    @Test
    void downloadsOnceWhileTheLanguageStaysTheSame() {
        mojang.serve("1.21.1", "ko_kr", KOREAN);
        VanillaTranslations translations = translations();

        translations.use(Language.KO);
        translations.use(Language.KO);
        io.completeOnIo(() -> {}).join();

        assertThat(mojang.requests).hasSize(4);
    }

    @Test
    void forgetsKoreanWhenSwitchedToEnglish() {
        mojang.serve("1.21.1", "ko_kr", KOREAN);
        VanillaTranslations translations = translations();
        translations.use(Language.KO);
        io.completeOnIo(() -> {}).join();

        translations.use(Language.EN);

        assertThat(translations.pattern("death.attack.fall")).isEmpty();
    }

    private VanillaTranslations translations() {
        return new VanillaTranslations(new LanguageFiles(directory, mojang), io, "1.21.1");
    }
}
