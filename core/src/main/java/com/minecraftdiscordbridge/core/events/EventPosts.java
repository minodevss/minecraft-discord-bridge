package com.minecraftdiscordbridge.core.events;

import com.minecraftdiscordbridge.core.config.EventsConfig;
import com.minecraftdiscordbridge.core.discord.Author;
import com.minecraftdiscordbridge.core.discord.Discord;
import com.minecraftdiscordbridge.core.discord.DiscordId;
import com.minecraftdiscordbridge.core.discord.OutboundMessage;
import com.minecraftdiscordbridge.core.i18n.Lang;
import com.minecraftdiscordbridge.core.platform.AdvancementFrame;
import com.minecraftdiscordbridge.core.platform.GameEvent;
import com.minecraftdiscordbridge.core.platform.RichText;
import com.minecraftdiscordbridge.core.text.SafeText;
import com.minecraftdiscordbridge.core.text.TextRenderer;
import com.minecraftdiscordbridge.core.text.Translations;
import com.minecraftdiscordbridge.core.text.markdown.DiscordEscaper;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public final class EventPosts {
    private static final Author SERVER = Author.server();

    private final EventsConfig config;
    private final Optional<DiscordId> channel;
    private final Lang lang;
    private final Optional<Translations> gameText;
    private final Discord discord;

    public EventPosts(
            EventsConfig config,
            Optional<DiscordId> channel,
            Lang lang,
            Optional<Translations> gameText,
            Discord discord) {
        this.config = config;
        this.channel = channel;
        this.lang = lang;
        this.gameText = gameText;
        this.discord = discord;
    }

    public void joined(GameEvent.PlayerJoined event) {
        if (config.join() && !event.vanished()) {
            post(lang.text("discord.event.join", name(event.player().name())));
        }
    }

    public void left(GameEvent.PlayerLeft event) {
        if (config.leave() && !event.vanished()) {
            post(lang.text("discord.event.leave", name(event.player().name())));
        }
    }

    public void switched(GameEvent.PlayerSwitchedServer event) {
        if (config.join() && event.from() != null) {
            post(lang.text("discord.event.switch", name(event.player().name()), name(event.to())));
        }
    }

    public void died(GameEvent.PlayerDied event) {
        String message = flatten(event.message());
        if (config.death() && !message.isEmpty()) {
            post(DiscordEscaper.escape(message));
        }
    }

    public void advanced(GameEvent.AdvancementEarned event) {
        String title = flatten(event.title());
        if (config.advancement() && !title.isEmpty()) {
            post(lang.text(advancementKey(event.frame()), name(event.player().name()), DiscordEscaper.escape(title)));
        }
    }

    public void serverStarted(boolean recovered) {
        if (!config.serverStart() || channel.isEmpty()) {
            return;
        }
        String text = lang.text(recovered ? "discord.event.server-recovered" : "discord.event.server-started");
        Optional<DiscordId> role = config.serverStartPingRole();
        if (role.isEmpty()) {
            post(text);
            return;
        }
        String ping = "<@&" + role.get() + "> ";
        discord.send(new OutboundMessage(channel.get(), SERVER, ping + text, Set.of(), Set.of(role.get())));
    }

    public Optional<OutboundMessage> serverStopped(boolean crashed) {
        if (!config.serverStop() || channel.isEmpty()) {
            return Optional.empty();
        }
        String text = lang.text(crashed ? "discord.event.server-crashed" : "discord.event.server-stopped");
        return Optional.of(OutboundMessage.of(channel.get(), SERVER, text));
    }

    private void post(String text) {
        if (channel.isPresent()) {
            discord.send(OutboundMessage.of(channel.get(), SERVER, text));
        }
    }

    private String flatten(RichText text) {
        String plain = gameText.map(translations -> TextRenderer.plain(text, translations))
                .orElse(text.plain());
        return SafeText.strip(plain).strip();
    }

    private static String advancementKey(AdvancementFrame frame) {
        return "discord.event.advancement." + frame.name().toLowerCase(Locale.ROOT);
    }

    private static String name(String text) {
        return DiscordEscaper.escape(SafeText.strip(text));
    }
}
