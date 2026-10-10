package com.minecraftdiscordbridge.core.chat;

import com.minecraftdiscordbridge.core.discord.DiscordId;
import com.minecraftdiscordbridge.core.discord.DiscordMessage;
import com.minecraftdiscordbridge.core.text.markdown.MentionResolver;
import java.util.Optional;

record MessageMentions(DiscordMessage message) implements MentionResolver {
    @Override
    public Optional<String> userName(long userId) {
        return Optional.ofNullable(message.mentionedUsers().get(new DiscordId(userId)));
    }

    @Override
    public Optional<Role> role(long roleId) {
        return Optional.ofNullable(message.mentionedRoles().get(new DiscordId(roleId)))
                .map(role -> new Role(role.name(), role.color()));
    }

    @Override
    public Optional<String> channelName(long channelId) {
        return Optional.ofNullable(message.mentionedChannels().get(new DiscordId(channelId)));
    }
}
