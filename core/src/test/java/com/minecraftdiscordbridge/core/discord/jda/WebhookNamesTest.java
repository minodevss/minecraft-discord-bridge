package com.minecraftdiscordbridge.core.discord.jda;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WebhookNamesTest {
    @Test
    void webhookNameIsAcceptedByDiscord() {
        assertThat(WebhookNames.sanitize(WebhookSender.WEBHOOK_NAME)).isEqualTo(WebhookSender.WEBHOOK_NAME);
    }

    @Test
    void keepsOrdinaryNames() {
        assertThat(WebhookNames.sanitize("Steve_99")).isEqualTo("Steve_99");
    }

    @Test
    void neutralizesDiscordInAnyCase() {
        assertThat(WebhookNames.sanitize("Discord")).isEqualTo("D1scord");
        assertThat(WebhookNames.sanitize("xDISCORDx")).isEqualTo("xD1SCORDx");
        assertThat(WebhookNames.sanitize("discord_fan")).isEqualTo("d1scord_fan");
    }

    @Test
    void neutralizesClydeInAnyCase() {
        assertThat(WebhookNames.sanitize("Clyde")).isEqualTo("C1yde");
        assertThat(WebhookNames.sanitize("notclyde")).isEqualTo("notc1yde");
    }

    @Test
    void removesInvisibleCharactersBeforeChecking() {
        assertThat(WebhookNames.sanitize("dis\u200Bcord")).isEqualTo("d1scord");
        assertThat(WebhookNames.sanitize("Ste\u0000ve" + Character.toString(0x202E)))
                .isEqualTo("Steve");
    }

    @Test
    void trimsAndCollapsesWhitespace() {
        assertThat(WebhookNames.sanitize("  Steve \t the\n Builder ")).isEqualTo("Steve the Builder");
    }

    @Test
    void cutsNamesToEightyCharacters() {
        assertThat(WebhookNames.sanitize("a".repeat(100))).hasSize(80);
    }

    @Test
    void neverSplitsAnEmojiWhenCutting() {
        String name = WebhookNames.sanitize("a".repeat(79) + "😀");

        assertThat(name).isEqualTo("a".repeat(79));
    }

    @Test
    void fallsBackWhenNothingVisibleRemains() {
        assertThat(WebhookNames.sanitize("")).isEqualTo("Player");
        assertThat(WebhookNames.sanitize(" \u200B\u0000 ")).isEqualTo("Player");
    }
}
