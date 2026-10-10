package com.minecraftdiscordbridge.core.setup;

import static org.assertj.core.api.Assertions.assertThat;

import com.minecraftdiscordbridge.core.config.DiscordConfig;
import com.minecraftdiscordbridge.core.discord.DiscordId;
import com.minecraftdiscordbridge.core.storage.ChannelBinding;
import com.minecraftdiscordbridge.core.storage.ChannelRole;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ChannelsTest {
    private static final DiscordId GUILD = new DiscordId(100000000000000000L);
    private static final ChannelBinding SETUP = new ChannelBinding(
            GUILD,
            Map.of(
                    ChannelRole.CHAT, new DiscordId(100000000000000001L),
                    ChannelRole.EVENTS, new DiscordId(100000000000000002L)));

    @Test
    void usesChannelsFromSetupWhenTheConfigLeavesThemEmpty() {
        Channels channels = Channels.resolve(config("", ""), Optional.of(SETUP));

        assertThat(channels)
                .isEqualTo(new Channels(
                        Optional.of(GUILD),
                        Optional.of(new DiscordId(100000000000000001L)),
                        Optional.of(new DiscordId(100000000000000002L))));
    }

    @Test
    void configOverridesSetup() {
        Channels channels = Channels.resolve(config("200000000000000001", ""), Optional.of(SETUP));

        assertThat(channels.chat()).contains(new DiscordId(200000000000000001L));
        assertThat(channels.events()).contains(new DiscordId(100000000000000002L));
    }

    @Test
    void eventsFallBackToTheChatChannel() {
        Channels channels = Channels.resolve(config("200000000000000001", ""), Optional.empty());

        assertThat(channels.guild()).isEmpty();
        assertThat(channels.events()).contains(new DiscordId(200000000000000001L));
    }

    private static DiscordConfig config(String chat, String events) {
        return new DiscordConfig(optional(chat), optional(events), Optional.empty(), true);
    }

    private static Optional<DiscordId> optional(String id) {
        return DiscordId.parse(id);
    }
}
