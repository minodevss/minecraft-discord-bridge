package com.minecraftdiscordbridge.core.discord;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class DiscordText {
    static final int MAX_MESSAGE_LENGTH = 2000;
    private static final String ELLIPSIS = "…";
    private static final Pattern MARKDOWN = Pattern.compile("[\\\\*_~`|]");

    private DiscordText() {}

    static String fit(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        int end = maxLength - ELLIPSIS.length();
        if (Character.isHighSurrogate(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(0, end) + ELLIPSIS;
    }

    static String speakerLine(String name, String text) {
        return "**" + escapeMarkdown(name) + "**: " + text;
    }

    static String escapeMarkdown(String text) {
        return MARKDOWN.matcher(text).replaceAll(match -> Matcher.quoteReplacement("\\" + match.group()));
    }
}
