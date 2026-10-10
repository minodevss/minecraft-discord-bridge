package com.minecraftdiscordbridge.core.chat;

import com.minecraftdiscordbridge.core.discord.DiscordMessage;
import com.minecraftdiscordbridge.core.text.MentionResolver;
import org.jspecify.annotations.Nullable;

record MessageMentions(DiscordMessage message) implements MentionResolver {
    @Override
    public @Nullable String userName(long userId) {
        return message.mentionedUsers().get(userId);
    }

    @Override
    public @Nullable Role role(long roleId) {
        DiscordMessage.MentionedRole role = message.mentionedRoles().get(roleId);
        if (role == null) {
            return null;
        }
        Integer color = role.color();
        return color == null ? Role.uncolored(role.name()) : Role.colored(role.name(), color);
    }

    @Override
    public @Nullable String channelName(long channelId) {
        return message.mentionedChannels().get(channelId);
    }
}
