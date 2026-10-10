package com.minecraftdiscordbridge.core.discord;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

public record DiscordMessage(
        DiscordId channelId,
        Sender author,
        String content,
        Map<DiscordId, String> mentionedUsers,
        Map<DiscordId, MentionedRole> mentionedRoles,
        Map<DiscordId, String> mentionedChannels,
        List<Attachment> attachments,
        Optional<Reply> reply,
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

    public record Sender(DiscordId id, String displayName, OptionalInt roleColor, boolean bot, boolean webhook) {}

    public record MentionedRole(String name, OptionalInt color) {}

    public record Attachment(String fileName, String url, boolean image) {}

    public record Reply(String authorName, String excerpt) {}
}
