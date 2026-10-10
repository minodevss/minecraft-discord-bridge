package com.minecraftdiscordbridge.core.discord;

import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public record DiscordMessage(
        long channelId,
        long messageId,
        Sender author,
        String content,
        Map<Long, String> mentionedUsers,
        Map<Long, MentionedRole> mentionedRoles,
        Map<Long, String> mentionedChannels,
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
            long id, String displayName, @Nullable Integer roleColor, boolean bot, boolean webhook) {}

    public record MentionedRole(String name, @Nullable Integer color) {}

    public record Attachment(
            String fileName, String url, @Nullable String contentType, boolean image, boolean video) {}

    public record Reply(String authorName, String excerpt) {}
}
