package com.minecraftdiscordbridge.core.discord;

import com.minecraftdiscordbridge.core.text.DiscordEscaper;
import com.minecraftdiscordbridge.core.text.Graphemes;

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
