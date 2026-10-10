package com.minecraftdiscordbridge.core.discord;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TokenRedactionTest {
    private static final String TOKEN = "bot-secret";

    @Test
    void settingsNeverPrintTheToken() {
        DiscordSettings settings = new DiscordSettings(
                TOKEN, Optional.of(new DiscordId(1)), Set.of(new DiscordId(2)), Author.server(), false, List.of());

        assertThat(settings.toString()).doesNotContain(TOKEN);
    }

    @Test
    void webhookCredentialsNeverPrintTheToken() {
        assertThat(new WebhookCredentials(new DiscordId(1), "hook-secret").toString())
                .doesNotContain("hook-secret");
    }
}
