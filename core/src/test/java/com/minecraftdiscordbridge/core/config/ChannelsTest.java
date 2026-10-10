package com.minecraftdiscordbridge.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.minecraftdiscordbridge.core.storage.ChannelBinding;
import com.minecraftdiscordbridge.core.storage.ChannelRole;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ChannelsTest {
    private static final String GUILD = "100000000000000000";
    private static final ChannelBinding SETUP = new ChannelBinding(
            GUILD,
            Map.of(
                    ChannelRole.CHAT, "100000000000000001",
                    ChannelRole.EVENTS, "100000000000000002",
                    ChannelRole.CONSOLE, "100000000000000003"));

    @Test
    void usesChannelsFromSetupWhenTheConfigLeavesThemEmpty() {
        Channels channels = Channels.resolve(config("", "", ""), Optional.of(SETUP));

        assertThat(channels)
                .isEqualTo(new Channels(
                        Optional.of(GUILD),
                        Optional.of("100000000000000001"),
                        Optional.of("100000000000000002"),
                        Optional.of("100000000000000003")));
    }

    @Test
    void configOverridesSetup() {
        Channels channels =
                Channels.resolve(config("200000000000000001", "", "200000000000000003"), Optional.of(SETUP));

        assertThat(channels.chat()).contains("200000000000000001");
        assertThat(channels.events()).contains("100000000000000002");
        assertThat(channels.console()).contains("200000000000000003");
    }

    @Test
    void eventsFallBackToTheChatChannelAndConsoleStaysOff() {
        Channels channels = Channels.resolve(config("200000000000000001", "", ""), Optional.empty());

        assertThat(channels.guild()).isEmpty();
        assertThat(channels.events()).contains("200000000000000001");
        assertThat(channels.console()).isEmpty();
    }

    private static DiscordConfig config(String chat, String events, String console) {
        return new DiscordConfig(optional(chat), optional(events), optional(console), true);
    }

    private static Optional<String> optional(String id) {
        return id.isEmpty() ? Optional.empty() : Optional.of(id);
    }
}
