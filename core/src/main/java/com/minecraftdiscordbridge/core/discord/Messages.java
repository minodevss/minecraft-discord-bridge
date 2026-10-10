package com.minecraftdiscordbridge.core.discord;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.dv8tion.jda.api.entities.Message.MentionType;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import net.dv8tion.jda.api.utils.messages.MessageRequest;

final class Messages {
    private static final EnumSet<MentionType> NO_MENTIONS = EnumSet.noneOf(MentionType.class);

    static {
        MessageRequest.setDefaultMentions(NO_MENTIONS);
        MessageRequest.setDefaultMentionRepliedUser(false);
    }

    private Messages() {}

    static MessageCreateData create(String content, Set<Long> pingUserIds) {
        List<String> users = pingUserIds.stream().map(Long::toUnsignedString).toList();
        return new MessageCreateBuilder()
                .setContent(DiscordText.fit(content, DiscordText.MAX_MESSAGE_LENGTH))
                .setAllowedMentions(NO_MENTIONS)
                .mentionUsers(users)
                .mentionRepliedUser(false)
                .build();
    }

    static MessageCreateData create(String content) {
        return create(content, Set.of());
    }

    static MessageEditData edit(String content) {
        return new MessageEditBuilder()
                .setContent(DiscordText.fit(content, DiscordText.MAX_MESSAGE_LENGTH))
                .setAllowedMentions(NO_MENTIONS)
                .mentionRepliedUser(false)
                .build();
    }
}
