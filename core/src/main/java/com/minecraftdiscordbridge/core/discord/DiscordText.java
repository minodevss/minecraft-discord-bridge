package com.minecraftdiscordbridge.core.discord;

import com.minecraftdiscordbridge.core.text.Graphemes;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class DiscordText {
    static final int MAX_MESSAGE_LENGTH = 2000;
    private static final String ELLIPSIS = "…";
    private static final Pattern MARKDOWN = Pattern.compile("[\\\\*_~`|]");

    private DiscordText() {}

    static String fit(String text, int maxLength) {
        return Graphemes.fit(text, maxLength, ELLIPSIS);
    }

    static String speakerLine(String name, String text) {
        return "**" + escapeMarkdown(name) + "**: " + text;
    }

    static String escapeMarkdown(String text) {
        return MARKDOWN.matcher(text).replaceAll(match -> Matcher.quoteReplacement("\\" + match.group()));
    }
}
