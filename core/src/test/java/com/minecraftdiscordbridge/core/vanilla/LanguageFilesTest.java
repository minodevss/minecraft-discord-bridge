package com.minecraftdiscordbridge.core.vanilla;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LanguageFilesTest {
    private static final String KOREAN = "{\"death.attack.fall\":\"%1$s이(가) 땅바닥으로 곤두박질쳤습니다\",\"pack.name\":1}";
    private static final String OLD_KOREAN = "{\"death.attack.fall\":\"%1$s이(가) 떨어졌습니다\"}";

    @TempDir
    Path directory;

    @Test
    void findsTheLanguageFileThroughTheVersionManifestAndAssetIndex() throws IOException {
        FakeFetcher mojang = new FakeFetcher().serve("1.21.1", "ko_kr", KOREAN);

        assertThat(new LanguageFiles(directory, mojang).load("1.21.1", "ko_kr"))
                .containsExactly(Map.entry("death.attack.fall", "%1$s이(가) 땅바닥으로 곤두박질쳤습니다"));
        assertThat(mojang.requests).hasSize(4).startsWith(LanguageFiles.MANIFEST);
        assertThat(mojang.requests.get(3)).isEqualTo(FakeFetcher.languageUri(FakeFetcher.sha1(KOREAN)));
    }

    @Test
    void picksTheExactVersionFromTheManifest() throws IOException {
        FakeFetcher mojang = new FakeFetcher().serve("26.1", "ko_kr", KOREAN).serve("1.21.1", "ko_kr", OLD_KOREAN);

        assertThat(new LanguageFiles(directory, mojang).load("26.1", "ko_kr"))
                .containsEntry("death.attack.fall", "%1$s이(가) 땅바닥으로 곤두박질쳤습니다");
        assertThat(new LanguageFiles(directory, mojang).load("1.21.1", "ko_kr"))
                .containsEntry("death.attack.fall", "%1$s이(가) 떨어졌습니다");
    }

    @Test
    void cachesTheFileUnderItsHash() throws IOException {
        new LanguageFiles(directory, new FakeFetcher().serve("1.21.1", "ko_kr", KOREAN)).load("1.21.1", "ko_kr");

        assertThat(cache().resolve(FakeFetcher.sha1(KOREAN) + ".json"))
                .usingCharset(StandardCharsets.UTF_8)
                .hasContent(KOREAN);
    }

    @Test
    void reusesTheCacheWithoutTheNetwork() throws IOException {
        new LanguageFiles(directory, new FakeFetcher().serve("1.21.1", "ko_kr", KOREAN)).load("1.21.1", "ko_kr");
        FakeFetcher offline = new FakeFetcher();

        assertThat(new LanguageFiles(directory, offline).load("1.21.1", "ko_kr"))
                .containsKey("death.attack.fall");
        assertThat(offline.requests).isEmpty();
    }

    @Test
    void downloadsAgainWhenTheCachedFileIsDamaged() throws IOException {
        FakeFetcher mojang = new FakeFetcher().serve("1.21.1", "ko_kr", KOREAN);
        new LanguageFiles(directory, mojang).load("1.21.1", "ko_kr");
        Files.writeString(cache().resolve(FakeFetcher.sha1(KOREAN) + ".json"), "{}");

        assertThat(new LanguageFiles(directory, mojang).load("1.21.1", "ko_kr")).containsKey("death.attack.fall");
        assertThat(cache().resolve(FakeFetcher.sha1(KOREAN) + ".json"))
                .usingCharset(StandardCharsets.UTF_8)
                .hasContent(KOREAN);
    }

    @Test
    void rejectsAFileWhoseSha1DoesNotMatchTheAssetIndex() {
        FakeFetcher mojang = new FakeFetcher().serve("1.21.1", "ko_kr", KOREAN, FakeFetcher.sha1(OLD_KOREAN));

        assertThatThrownBy(() -> new LanguageFiles(directory, mojang).load("1.21.1", "ko_kr"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("SHA-1");
        assertThat(cache()).doesNotExist();
    }

    @Test
    void refusesAHashThatIsNotSha1() {
        FakeFetcher mojang =
                new FakeFetcher().serve("1.21.1", "ko_kr", KOREAN, "../../../../config.yml.000000000000000");

        assertThatThrownBy(() -> new LanguageFiles(directory, mojang).load("1.21.1", "ko_kr"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("no SHA-1");
        assertThat(mojang.requests).hasSize(3);
    }

    @Test
    void failsWhenMojangDoesNotListTheVersion() {
        FakeFetcher mojang = new FakeFetcher().serve("1.21.1", "ko_kr", KOREAN);

        assertThatThrownBy(() -> new LanguageFiles(directory, mojang).load("1.21.99", "ko_kr"))
                .isInstanceOf(IOException.class)
                .hasMessage("Mojang's version list has no Minecraft 1.21.99");
    }

    @Test
    void failsWhenTheVersionHasNoSuchLanguage() {
        FakeFetcher mojang = new FakeFetcher().serve("1.21.1", "ko_kr", KOREAN);

        assertThatThrownBy(() -> new LanguageFiles(directory, mojang).load("1.21.1", "xx_xx"))
                .isInstanceOf(IOException.class)
                .hasMessage("Minecraft 1.21.1 has no xx_xx language file");
    }

    @Test
    void failsWithoutTheNetworkWhenNothingIsCached() {
        assertThatThrownBy(() -> new LanguageFiles(directory, new FakeFetcher()).load("1.21.1", "ko_kr"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("No network in tests");
    }

    private Path cache() {
        return directory.resolve("data").resolve("cache").resolve("lang");
    }
}
