package com.minecraftdiscordbridge.core.discord.jda;

import com.minecraftdiscordbridge.core.text.markdown.DiscordEscaper;

final class DiscordText {
    private DiscordText() {}

    static String speakerLine(String name, String text) {
        return "**" + DiscordEscaper.escape(name) + "**: " + text;
    }
}
