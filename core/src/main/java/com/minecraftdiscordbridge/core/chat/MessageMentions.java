package com.minecraftdiscordbridge.core.chat;

import com.minecraftdiscordbridge.core.discord.DiscordId;
import com.minecraftdiscordbridge.core.discord.DiscordMessage;
import com.minecraftdiscordbridge.core.text.markdown.MentionResolver;
import org.jspecify.annotations.Nullable;

record MessageMentions(DiscordMessage message) implements MentionResolver {
    @Override
    public @Nullable String userName(long userId) {
        return message.mentionedUsers().get(new DiscordId(userId));
    }

    @Override
    public @Nullable Role role(long roleId) {
        DiscordMessage.MentionedRole role = message.mentionedRoles().get(new DiscordId(roleId));
        if (role == null) {
            return null;
        }
        return new Role(role.name(), role.color());
    }

    @Override
    public @Nullable String channelName(long channelId) {
        return message.mentionedChannels().get(new DiscordId(channelId));
    }
}
