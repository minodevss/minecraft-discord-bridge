package com.minecraftdiscordbridge.core.discord.jda;

import com.minecraftdiscordbridge.core.text.Graphemes;
import com.minecraftdiscordbridge.core.text.markdown.DiscordEscaper;

final class DiscordText {
    static final int MAX_MESSAGE_LENGTH = 2000;
    private static final String ELLIPSIS = "…";

    private DiscordText() {}

    static String fit(String text, int maxLength) {
        return Graphemes.fit(text, maxLength, ELLIPSIS);
    }

    static String speakerLine(String name, String text) {
        return "**" + DiscordEscaper.escape(name) + "**: " + text;
    }
}
