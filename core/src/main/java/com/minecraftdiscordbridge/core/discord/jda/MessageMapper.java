package com.minecraftdiscordbridge.core.discord.jda;

import com.minecraftdiscordbridge.core.discord.DiscordId;
import com.minecraftdiscordbridge.core.discord.DiscordMessage;
import com.minecraftdiscordbridge.core.text.Graphemes;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
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
                new DiscordId(message.getChannel().getIdLong()),
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
                new DiscordId(author.getIdLong()),
                displayName(author, member),
                member == null ? OptionalInt.empty() : color(member.getColors()),
                author.isBot(),
                message.isWebhookMessage());
    }

    private static Map<DiscordId, String> users(Mentions mentions) {
        Map<DiscordId, String> names = new HashMap<>();
        for (User user : mentions.getUsers()) {
            names.put(new DiscordId(user.getIdLong()), user.getEffectiveName());
        }
        for (Member member : mentions.getMembers()) {
            names.put(new DiscordId(member.getIdLong()), member.getEffectiveName());
        }
        return names;
    }

    private static Map<DiscordId, DiscordMessage.MentionedRole> roles(Mentions mentions) {
        Map<DiscordId, DiscordMessage.MentionedRole> roles = new HashMap<>();
        mentions.getRoles()
                .forEach(role -> roles.put(
                        new DiscordId(role.getIdLong()),
                        new DiscordMessage.MentionedRole(role.getName(), color(role.getColors()))));
        return roles;
    }

    private static Map<DiscordId, String> channels(Mentions mentions) {
        Map<DiscordId, String> channels = new HashMap<>();
        for (GuildChannel channel : mentions.getChannels()) {
            channels.put(new DiscordId(channel.getIdLong()), channel.getName());
        }
        return channels;
    }

    private static DiscordMessage.Attachment attachment(Message.Attachment attachment) {
        return new DiscordMessage.Attachment(attachment.getFileName(), attachment.getUrl(), attachment.isImage());
    }

    private static Optional<DiscordMessage.Reply> reply(Message message) {
        Message referenced = message.getReferencedMessage();
        if (referenced == null || !message.getMessageSnapshots().isEmpty()) {
            return Optional.empty();
        }
        String excerpt = referenced.getContentRaw();
        return Optional.of(new DiscordMessage.Reply(
                displayName(referenced.getAuthor(), referenced.getMember()),
                excerpt.length() <= REPLY_EXCERPT_LENGTH
                        ? excerpt
                        : Graphemes.fit(excerpt, REPLY_EXCERPT_LENGTH + 1, Graphemes.ELLIPSIS)));
    }

    private static String displayName(User user, @Nullable Member member) {
        return member == null ? user.getEffectiveName() : member.getEffectiveName();
    }

    private static OptionalInt color(RoleColors colors) {
        return colors.isDefault() ? OptionalInt.empty() : OptionalInt.of(colors.getPrimaryRaw() & 0xFFFFFF);
    }
}
