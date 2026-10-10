package com.minecraftdiscordbridge.velocity;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class SkinTexturesTest {
    private static final String HASH = "1a4af718455d4aab528e7a61f86fa25e6a369d1768dcb13f7df319a713eb810b";

    @Test
    void readsSkinTextureIdFromTexturesProperty() {
        String textures = encode("""
                {"timestamp":1700000000000,"profileId":"069a79f444e94726a5befca90e38aaf5","profileName":"Notch",
                 "textures":{"SKIN":{"url":"http://textures.minecraft.net/texture/%s"},
                 "CAPE":{"url":"http://textures.minecraft.net/texture/cape"}}}
                """.formatted(HASH));

        assertThat(SkinTextures.id(textures)).isEqualTo(HASH);
    }

    @Test
    void readsSlimSkinWithMetadata() {
        String textures = encode("""
                {"textures":{"SKIN":{"url":"https://textures.minecraft.net/texture/%s","metadata":{"model":"slim"}}}}
                """.formatted(HASH));

        assertThat(SkinTextures.id(textures)).isEqualTo(HASH);
    }

    @Test
    void returnsNullWithoutSkin() {
        assertThat(SkinTextures.id(encode("{\"textures\":{}}"))).isNull();
        assertThat(SkinTextures.id(encode("{}"))).isNull();
    }

    @Test
    void returnsNullForMalformedProperty() {
        assertThat(SkinTextures.id("not base64!")).isNull();
        assertThat(SkinTextures.id(encode("not json"))).isNull();
        assertThat(SkinTextures.id(encode("{\"textures\":[]}"))).isNull();
        assertThat(SkinTextures.id(encode("{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/\"}}}")))
                .isNull();
    }

    private static String encode(String json) {
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}
