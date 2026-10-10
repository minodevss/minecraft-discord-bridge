package com.minecraftdiscordbridge.core.discord;

import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public record DiscordMessage(
        DiscordId channelId,
        Sender author,
        String content,
        Map<DiscordId, String> mentionedUsers,
        Map<DiscordId, MentionedRole> mentionedRoles,
        Map<DiscordId, String> mentionedChannels,
        List<Attachment> attachments,
        @Nullable Reply reply,
        List<String> stickers,
        boolean forwarded,
        boolean poll,
        boolean voiceMessage) {
    public DiscordMessage {
        mentionedUsers = Map.copyOf(mentionedUsers);
        mentionedRoles = Map.copyOf(mentionedRoles);
        mentionedChannels = Map.copyOf(mentionedChannels);
        attachments = List.copyOf(attachments);
        stickers = List.copyOf(stickers);
    }

    public record Sender(
            DiscordId id, String displayName, @Nullable Integer roleColor, boolean bot, boolean webhook) {}

    public record MentionedRole(String name, @Nullable Integer color) {}

    public record Attachment(String fileName, String url, boolean image) {}

    public record Reply(String authorName, String excerpt) {}
}
