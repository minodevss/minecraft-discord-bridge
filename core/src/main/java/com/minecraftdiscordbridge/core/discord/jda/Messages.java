package com.minecraftdiscordbridge.core.discord.jda;

import com.minecraftdiscordbridge.core.discord.DiscordId;
import com.minecraftdiscordbridge.core.discord.OutboundMessage;
import com.minecraftdiscordbridge.core.text.Graphemes;
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

    static MessageCreateData create(String content, Set<DiscordId> pingUserIds, Set<DiscordId> pingRoleIds) {
        MessageCreateBuilder message = new MessageCreateBuilder()
                .setContent(Graphemes.fit(content, OutboundMessage.MAX_LENGTH, Graphemes.ELLIPSIS))
                .setAllowedMentions(NO_MENTIONS)
                .mentionUsers(ids(pingUserIds))
                .mentionRepliedUser(false);
        if (!pingRoleIds.isEmpty()) {
            message.mentionRoles(ids(pingRoleIds));
        }
        return message.build();
    }

    static MessageCreateData create(String content, Set<DiscordId> pingUserIds) {
        return create(content, pingUserIds, Set.of());
    }

    static MessageCreateData create(String content) {
        return create(content, Set.of(), Set.of());
    }

    private static List<String> ids(Set<DiscordId> ids) {
        return ids.stream().map(DiscordId::toString).toList();
    }

    static MessageEditData edit(String content) {
        return new MessageEditBuilder()
                .setContent(Graphemes.fit(content, OutboundMessage.MAX_LENGTH, Graphemes.ELLIPSIS))
                .setAllowedMentions(NO_MENTIONS)
                .mentionRepliedUser(false)
                .build();
    }
}
