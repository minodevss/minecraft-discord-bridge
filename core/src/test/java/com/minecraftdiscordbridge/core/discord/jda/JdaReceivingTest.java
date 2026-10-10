package com.minecraftdiscordbridge.core.discord.jda;

import static com.minecraftdiscordbridge.core.discord.jda.DiscordPayloads.APPLICATION_ID;
import static com.minecraftdiscordbridge.core.discord.jda.DiscordPayloads.BOT_ID;
import static com.minecraftdiscordbridge.core.discord.jda.DiscordPayloads.CHANNEL_ID;
import static com.minecraftdiscordbridge.core.discord.jda.DiscordPayloads.FOREIGN_WEBHOOK_ID;
import static com.minecraftdiscordbridge.core.discord.jda.DiscordPayloads.FRIEND_ID;
import static com.minecraftdiscordbridge.core.discord.jda.DiscordPayloads.OTHER_BOT_ID;
import static com.minecraftdiscordbridge.core.discord.jda.DiscordPayloads.PLAYER_ID;
import static com.minecraftdiscordbridge.core.discord.jda.DiscordPayloads.ROLE_ID;
import static com.minecraftdiscordbridge.core.fakes.Waits.take;
import static org.assertj.core.api.Assertions.assertThat;

import com.minecraftdiscordbridge.core.discord.DiscordMessage;
import java.util.OptionalInt;
import net.dv8tion.jda.api.utils.data.DataArray;
import net.dv8tion.jda.api.utils.data.DataObject;
import org.junit.jupiter.api.Test;

class JdaReceivingTest extends JdaTestBase {
    @Test
    void relaysMessagesFromTheBoundChannelWithResolvedDetails() {
        bridge = startReady();
        DataObject friend = DataObject.fromJson(DiscordPayloads.user(FRIEND_ID, "Friend", false))
                .put("member", DiscordPayloads.member("Bestie"));
        DataObject message = discord.message(
                        PLAYER_ID, "hi <@" + FRIEND_ID + "> <@&" + ROLE_ID + "> <#" + CHANNEL_ID + ">")
                .put("member", DiscordPayloads.member("Alex"))
                .put("mentions", DataArray.empty().add(friend))
                .put("mention_roles", DataArray.empty().add(Long.toString(ROLE_ID)))
                .put("attachments", DataArray.empty().add(attachment()))
                .put("sticker_items", DataArray.empty().add(sticker()))
                .put("message_reference", reference(0))
                .put("referenced_message", discord.message(FRIEND_ID, "x".repeat(100)));

        discord.sendMessage(message);

        DiscordMessage received = take(listener.messages);
        assertThat(received.channelId()).isEqualTo(CHANNEL);
        assertThat(received.author())
                .isEqualTo(new DiscordMessage.Sender(PLAYER, "Alex", OptionalInt.of(0x3498db), false, false));
        assertThat(received.content()).startsWith("hi <@" + FRIEND_ID + ">");
        assertThat(received.mentionedUsers()).containsEntry(FRIEND, "Bestie");
        assertThat(received.mentionedRoles())
                .containsEntry(ROLE, new DiscordMessage.MentionedRole("Builders", OptionalInt.of(0x3498db)));
        assertThat(received.mentionedChannels()).containsEntry(CHANNEL, "minecraft");
        assertThat(received.attachments())
                .containsExactly(new DiscordMessage.Attachment("cat.png", "https://cdn.example.com/cat.png", true));
        assertThat(received.stickers()).containsExactly("wave");
        assertThat(received.reply()).isPresent();
        assertThat(received.reply().orElseThrow().authorName()).isEqualTo("Author" + FRIEND_ID);
        assertThat(received.reply().orElseThrow().excerpt()).isEqualTo("x".repeat(80) + "…");
        assertThat(received.forwarded()).isFalse();
        assertThat(received.poll()).isFalse();
        assertThat(received.voiceMessage()).isFalse();
    }

    @Test
    void marksForwardedAndVoiceMessages() {
        bridge = startReady();
        DataObject snapshot = DataObject.empty()
                .put("message", discord.message(FRIEND_ID, "original").remove("author"));

        discord.sendMessage(discord.message(PLAYER_ID, "")
                .put("message_reference", reference(1))
                .put("message_snapshots", DataArray.empty().add(snapshot)));
        discord.sendMessage(discord.message(PLAYER_ID, "").put("flags", 1 << 13));

        DiscordMessage forwarded = take(listener.messages);
        DiscordMessage voice = take(listener.messages);
        assertThat(forwarded.forwarded()).isTrue();
        assertThat(forwarded.reply()).isEmpty();
        assertThat(voice.voiceMessage()).isTrue();
    }

    @Test
    void ignoresBotsWebhooksAndItsOwnMessages() {
        bridge = startReady();

        discord.sendMessage(discord.message(OTHER_BOT_ID, "beep")
                .put("author", DataObject.fromJson(DiscordPayloads.user(OTHER_BOT_ID, "Other", true))));
        discord.sendMessage(
                discord.message(FOREIGN_WEBHOOK_ID, "hook").put("webhook_id", Long.toString(FOREIGN_WEBHOOK_ID)));
        discord.sendMessage(discord.message(FOREIGN_WEBHOOK_ID + 1, "ours")
                .put("webhook_id", Long.toString(FOREIGN_WEBHOOK_ID + 1))
                .put("application_id", Long.toString(APPLICATION_ID)));
        discord.sendMessage(discord.message(BOT_ID, "self"));
        discord.sendMessage(discord.message(PLAYER_ID, "real").put("channel_id", "999"));
        discord.sendMessage(discord.message(PLAYER_ID, "real"));

        assertThat(take(listener.messages).content()).isEqualTo("real");
        assertThat(listener.messages).isEmpty();
    }
}
