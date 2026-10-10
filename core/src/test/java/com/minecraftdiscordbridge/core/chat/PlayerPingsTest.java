package com.minecraftdiscordbridge.core.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.minecraftdiscordbridge.core.config.ChatConfig;
import com.minecraftdiscordbridge.core.discord.DiscordId;
import com.minecraftdiscordbridge.core.platform.PlayerProfile;
import com.minecraftdiscordbridge.core.storage.Edition;
import com.minecraftdiscordbridge.core.storage.Link;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlayerPingsTest {
    private static final Link BEDROCK = new Link(
            UUID.randomUUID(), new DiscordId(100000000000000001L), ".Bedrock_Kid", Edition.BEDROCK, Instant.EPOCH);

    @Test
    void pingsFloodgateNamesAndEscapesTheRest() {
        PlayerPings.Pinged pinged = PlayerPings.escape("# hi @.bedrock_kid, *you*", List.of(BEDROCK));

        assertThat(pinged.text()).isEqualTo("\\# hi <@100000000000000001>, \\*you\\*");
        assertThat(pinged.userIds()).containsExactly(new DiscordId(100000000000000001L));
    }

    @Test
    void escapesTextAfterAPingLikeTheMiddleOfALine() {
        PlayerPings.Pinged pinged = PlayerPings.escape("@.bedrock_kid - see 1. above", List.of(BEDROCK));

        assertThat(pinged.text()).isEqualTo("<@100000000000000001> - see 1. above");
    }

    @Test
    void leavesAtNamesInsideLinksAlone() {
        PlayerPings.Pinged pinged =
                PlayerPings.escape("look https://example.com/@.Bedrock_Kid/page ok", List.of(BEDROCK));

        assertThat(pinged.text()).isEqualTo("look https://example.com/@.Bedrock_Kid/page ok");
        assertThat(pinged.userIds()).isEmpty();
    }

    @Test
    void ignoresAtSignsInsideWords() {
        assertThat(PlayerPings.escape("mail me@.Bedrock_Kid", List.of(BEDROCK)).userIds())
                .isEmpty();
    }

    @Test
    void buildsAvatarUrlsFromTheTextureOrTheFallback() {
        ChatConfig chat = new ChatConfig(
                true,
                true,
                "<author>: <message>",
                "https://crafthead.net/helm/{texture}",
                "https://example.com/{name}/{uuid}",
                false,
                List.of());
        UUID id = UUID.fromString("00000000-0000-4000-8000-000000000001");

        assertThat(Avatars.url(chat, new PlayerProfile(id, "Steve", "Steve", "abc123", false)))
                .contains("https://crafthead.net/helm/abc123");
        assertThat(Avatars.url(chat, new PlayerProfile(id, ".Kid Two", ".Kid Two", null, true)))
                .contains("https://example.com/.Kid%20Two/" + id);
    }

    @Test
    void trustsOnlyHttpsDiscordAttachmentHosts() {
        assertThat(DiscordChat.fromDiscord("https://cdn.discordapp.com/attachments/1/2/a.png"))
                .isTrue();
        assertThat(DiscordChat.fromDiscord("https://media.discordapp.net/attachments/1/2/a.png"))
                .isTrue();
        assertThat(DiscordChat.fromDiscord("http://cdn.discordapp.com/a.png")).isFalse();
        assertThat(DiscordChat.fromDiscord("https://cdn.discordapp.com.evil.example/a.png"))
                .isFalse();
        assertThat(DiscordChat.fromDiscord("https://user@cdn.discordapp.com/a.png"))
                .isFalse();
        assertThat(DiscordChat.fromDiscord("not a url")).isFalse();
    }
}
