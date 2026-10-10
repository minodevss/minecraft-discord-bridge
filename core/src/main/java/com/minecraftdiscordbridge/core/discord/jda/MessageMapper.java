package com.minecraftdiscordbridge.core.discord.jda;

import com.minecraftdiscordbridge.core.discord.DiscordMessage;
import com.minecraftdiscordbridge.core.text.Graphemes;
import java.util.HashMap;
import java.util.Map;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Mentions;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.RoleColors;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.sticker.StickerItem;
import org.jspecify.annotations.Nullable;

final class MessageMapper {
    static final int REPLY_EXCERPT_LENGTH = 80;

    private MessageMapper() {}

    static DiscordMessage map(Message message) {
        return new DiscordMessage(
                message.getChannel().getIdLong(),
                sender(message),
                message.getContentRaw(),
                users(message.getMentions()),
                roles(message.getMentions()),
                channels(message.getMentions()),
                message.getAttachments().stream().map(MessageMapper::attachment).toList(),
                reply(message),
                message.getStickers().stream().map(StickerItem::getName).toList(),
                !message.getMessageSnapshots().isEmpty(),
                message.getPoll() != null,
                message.isVoiceMessage());
    }

    private static DiscordMessage.Sender sender(Message message) {
        User author = message.getAuthor();
        Member member = message.getMember();
        return new DiscordMessage.Sender(
                author.getIdLong(),
                displayName(author, member),
                member == null ? null : color(member.getColors()),
                author.isBot(),
                message.isWebhookMessage());
    }

    private static Map<Long, String> users(Mentions mentions) {
        Map<Long, String> names = new HashMap<>();
        for (User user : mentions.getUsers()) {
            names.put(user.getIdLong(), user.getEffectiveName());
        }
        for (Member member : mentions.getMembers()) {
            names.put(member.getIdLong(), member.getEffectiveName());
        }
        return names;
    }

    private static Map<Long, DiscordMessage.MentionedRole> roles(Mentions mentions) {
        Map<Long, DiscordMessage.MentionedRole> roles = new HashMap<>();
        mentions.getRoles()
                .forEach(role -> roles.put(
                        role.getIdLong(), new DiscordMessage.MentionedRole(role.getName(), color(role.getColors()))));
        return roles;
    }

    private static Map<Long, String> channels(Mentions mentions) {
        Map<Long, String> channels = new HashMap<>();
        for (GuildChannel channel : mentions.getChannels()) {
            channels.put(channel.getIdLong(), channel.getName());
        }
        return channels;
    }

    private static DiscordMessage.Attachment attachment(Message.Attachment attachment) {
        return new DiscordMessage.Attachment(attachment.getFileName(), attachment.getUrl(), attachment.isImage());
    }

    private static DiscordMessage.@Nullable Reply reply(Message message) {
        Message referenced = message.getReferencedMessage();
        if (referenced == null || !message.getMessageSnapshots().isEmpty()) {
            return null;
        }
        String excerpt = referenced.getContentRaw();
        return new DiscordMessage.Reply(
                displayName(referenced.getAuthor(), referenced.getMember()),
                excerpt.length() <= REPLY_EXCERPT_LENGTH
                        ? excerpt
                        : Graphemes.fit(excerpt, REPLY_EXCERPT_LENGTH + 1, Graphemes.ELLIPSIS));
    }

    private static String displayName(User user, @Nullable Member member) {
        return member == null ? user.getEffectiveName() : member.getEffectiveName();
    }

    private static @Nullable Integer color(RoleColors colors) {
        return colors.isDefault() ? null : colors.getPrimaryRaw() & 0xFFFFFF;
    }
}
