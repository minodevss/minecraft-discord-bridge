package com.minecraftdiscordbridge.core.chat;

import com.minecraftdiscordbridge.core.config.ChatConfig;
import com.minecraftdiscordbridge.core.discord.DiscordId;
import com.minecraftdiscordbridge.core.discord.DiscordMessage;
import com.minecraftdiscordbridge.core.i18n.Lang;
import com.minecraftdiscordbridge.core.platform.Delivery;
import com.minecraftdiscordbridge.core.platform.Platform;
import com.minecraftdiscordbridge.core.text.Format;
import com.minecraftdiscordbridge.core.text.Format.Slot;
import com.minecraftdiscordbridge.core.text.SafeText;
import com.minecraftdiscordbridge.core.text.TextRenderer;
import com.minecraftdiscordbridge.core.text.markdown.DiscordMarkdown;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;

public final class DiscordChat {
    public static final Set<String> PLACEHOLDERS = Set.of("author", "message");
    private static final Set<String> ATTACHMENT_HOSTS = Set.of("cdn.discordapp.com", "media.discordapp.net");

    private final ChatConfig config;
    private final Optional<DiscordId> channel;
    private final Format format;
    private final Lang lang;
    private final Platform platform;
    private final Supplier<Set<UUID>> hidden;

    public DiscordChat(
            ChatConfig config,
            Optional<DiscordId> channel,
            Format format,
            Lang lang,
            Platform platform,
            Supplier<Set<UUID>> hidden) {
        this.config = config;
        this.channel = channel;
        this.format = format;
        this.lang = lang;
        this.platform = platform;
        this.hidden = hidden;
    }

    public static Format compileFormat(String template) {
        return Format.compile("chat.format", template, PLACEHOLDERS);
    }

    public void onMessage(DiscordMessage message) {
        if (!config.discordToMinecraft()
                || channel.isEmpty()
                || !message.channelId().equals(channel.get())) {
            return;
        }
        if (Blocklist.blocks(config.blockedWords(), message.content())) {
            return;
        }
        Component body = body(message);
        if (TextRenderer.plain(body).isBlank()) {
            return;
        }
        Component line =
                format.render(Slot.component("author", author(message.author())), Slot.component("message", body));
        if (message.reply().isPresent()) {
            line = Component.textOfChildren(replyLine(message.reply().get()), Component.newline(), line);
        }
        platform.deliver(new Delivery(TextRenderer.toJson(line, platform.info().dataVersion()), hidden.get()));
    }

    private Component body(DiscordMessage message) {
        List<Component> parts = new ArrayList<>();
        if (message.forwarded()) {
            parts.add(lang.component("game.chat.forwarded"));
        }
        if (!message.content().isBlank()) {
            parts.add(DiscordMarkdown.parse(message.content(), new MessageMentions(message)));
        }
        if (message.voiceMessage()) {
            parts.add(lang.component("game.chat.voice"));
        } else {
            message.attachments().forEach(attachment -> parts.add(attachment(attachment)));
        }
        message.stickers().forEach(name -> parts.add(lang.component("game.chat.sticker", Slot.text("name", name))));
        if (message.poll()) {
            parts.add(lang.component("game.chat.poll"));
        }
        return Component.join(JoinConfiguration.separator(Component.space()), parts);
    }

    private static Component author(DiscordMessage.Sender sender) {
        Component name = Component.text(SafeText.strip(sender.displayName()));
        OptionalInt color = sender.roleColor();
        return color.isEmpty() ? name : name.color(TextColor.color(color.getAsInt()));
    }

    private Component replyLine(DiscordMessage.Reply reply) {
        return lang.component(
                "game.chat.reply",
                Slot.text("author", reply.authorName()),
                Slot.text("excerpt", reply.excerpt().replaceAll("\\s+", " ").strip()));
    }

    private Component attachment(DiscordMessage.Attachment attachment) {
        Component label = lang.component(
                attachment.image() ? "game.chat.image" : "game.chat.file", Slot.text("name", attachment.fileName()));
        if (!fromDiscord(attachment.url())) {
            return label;
        }
        return label.clickEvent(ClickEvent.openUrl(attachment.url()))
                .hoverEvent(HoverEvent.showText(lang.component("game.chat.open")));
    }

    static boolean fromDiscord(String url) {
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            return "https".equalsIgnoreCase(uri.getScheme())
                    && host != null
                    && uri.getRawUserInfo() == null
                    && (uri.getPort() == -1 || uri.getPort() == 443)
                    && ATTACHMENT_HOSTS.contains(host.toLowerCase(Locale.ROOT));
        } catch (URISyntaxException invalid) {
            return false;
        }
    }
}
