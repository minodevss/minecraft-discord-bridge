package com.minecraftdiscordbridge.core.discord;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TokenRedactionTest {
    private static final String TOKEN = "bot-secret";

    @Test
    void settingsNeverPrintTheToken() {
        DiscordSettings settings = new DiscordSettings(TOKEN, 1, Set.of(2L), Author.server(), true, false, List.of());

        assertThat(settings.toString()).doesNotContain(TOKEN);
    }

    @Test
    void webhookCredentialsNeverPrintTheToken() {
        assertThat(new WebhookCredentials(1, "hook-secret").toString()).doesNotContain("hook-secret");
    }
}
