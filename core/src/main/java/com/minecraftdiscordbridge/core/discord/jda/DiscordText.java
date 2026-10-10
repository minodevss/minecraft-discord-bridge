package com.minecraftdiscordbridge.core.discord.jda;

import com.minecraftdiscordbridge.core.discord.OutboundMessage;
import com.minecraftdiscordbridge.core.text.markdown.DiscordEscaper;

final class DiscordText {
    private DiscordText() {}

    static String speakerLine(String name, String text) {
        return "**" + DiscordEscaper.escape(name) + "**: " + text;
    }

    static String asBot(OutboundMessage message) {
        return message.author().isServer()
                ? message.text()
                : speakerLine(message.author().name(), message.text());
    }
}
