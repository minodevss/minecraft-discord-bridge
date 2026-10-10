package com.minecraftdiscordbridge.paper;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import org.junit.jupiter.api.Test;

class SkinTexturesTest {
    @Test
    void takesLastPathSegmentOfTextureUrl() throws MalformedURLException {
        URL skin = url(
                "http://textures.minecraft.net/texture/1a4af718455d4aab528e7a61f86fa25e6a369d1768dcb13f7df319a713eb810b");

        assertThat(SkinTextures.id(skin)).isEqualTo("1a4af718455d4aab528e7a61f86fa25e6a369d1768dcb13f7df319a713eb810b");
    }

    @Test
    void ignoresQueryString() throws MalformedURLException {
        assertThat(SkinTextures.id(url("https://textures.minecraft.net/texture/abc123?v=2")))
                .isEqualTo("abc123");
    }

    @Test
    void returnsNullWithoutSkin() {
        assertThat(SkinTextures.id(null)).isNull();
    }

    @Test
    void returnsNullWhenPathEndsWithSlash() throws MalformedURLException {
        assertThat(SkinTextures.id(url("http://textures.minecraft.net/texture/")))
                .isNull();
    }

    @Test
    void returnsNullWithoutPath() throws MalformedURLException {
        assertThat(SkinTextures.id(url("http://textures.minecraft.net"))).isNull();
    }

    private static URL url(String value) throws MalformedURLException {
        return URI.create(value).toURL();
    }
}
