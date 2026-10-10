package com.minecraftdiscordbridge.core.text;

import net.kyori.adventure.text.Component;

public final class DiscordMarkdown {
    public static final int MAX_LENGTH = 4000;
    public static final int MAX_DEPTH = 16;

    private DiscordMarkdown() {}

    public static Component parse(String markdown) {
        return parse(markdown, MentionResolver.NONE);
    }

    public static Component parse(String markdown, MentionResolver mentions) {
        return parse(markdown, mentions, MAX_DEPTH);
    }

    static Component parse(String markdown, MentionResolver mentions, int maxDepth) {
        return new MarkdownParser(SafeText.strip(truncate(markdown)), mentions, maxDepth).parse();
    }

    static String truncate(String markdown) {
        if (markdown.length() <= MAX_LENGTH) {
            return markdown;
        }
        int cut = MAX_LENGTH - 1;
        if (Character.isHighSurrogate(markdown.charAt(cut - 1))) {
            cut--;
        }
        return markdown.substring(0, cut) + "…";
    }
}
