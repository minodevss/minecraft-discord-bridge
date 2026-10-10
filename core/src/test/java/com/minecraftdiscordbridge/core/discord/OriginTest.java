package com.minecraftdiscordbridge.core.discord;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OriginTest {
    @Test
    void neverRelaysItsOwnMessages() {
        assertThat(Origin.SELF.relayed(true, true)).isFalse();
        assertThat(Origin.OWN_WEBHOOK.relayed(true, true)).isFalse();
    }

    @Test
    void relaysBotsAndWebhooksOnlyWhenAskedTo() {
        assertThat(Origin.BOT.relayed(false, true)).isFalse();
        assertThat(Origin.BOT.relayed(true, false)).isTrue();
        assertThat(Origin.WEBHOOK.relayed(true, false)).isFalse();
        assertThat(Origin.WEBHOOK.relayed(false, true)).isTrue();
    }

    @Test
    void alwaysRelaysPeople() {
        assertThat(Origin.USER.relayed(false, false)).isTrue();
    }
}
