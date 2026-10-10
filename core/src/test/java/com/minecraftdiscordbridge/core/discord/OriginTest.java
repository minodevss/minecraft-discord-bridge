package com.minecraftdiscordbridge.core.discord;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OriginTest {
    @Test
    void relaysOnlyPeople() {
        assertThat(Origin.USER.relayed()).isTrue();
        assertThat(Origin.SELF.relayed()).isFalse();
        assertThat(Origin.OWN_WEBHOOK.relayed()).isFalse();
        assertThat(Origin.BOT.relayed()).isFalse();
        assertThat(Origin.WEBHOOK.relayed()).isFalse();
    }
}
