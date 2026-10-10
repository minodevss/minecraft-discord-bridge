package com.minecraftdiscordbridge.core.discord;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class SecretsTest {
    @Test
    void settingsNeverPrintTheToken() {
        assertThat(TestSupport.settings(List.of()).toString()).doesNotContain(FakeDiscord.TOKEN);
    }

    @Test
    void webhookCredentialsNeverPrintTheToken() {
        assertThat(new WebhookCredentials(1, "hook-secret").toString()).doesNotContain("hook-secret");
    }
}
