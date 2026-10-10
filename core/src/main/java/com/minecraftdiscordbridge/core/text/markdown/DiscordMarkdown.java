package com.minecraftdiscordbridge.core.text.markdown;

import com.minecraftdiscordbridge.core.text.Graphemes;
import com.minecraftdiscordbridge.core.text.SafeText;
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
        return new MarkdownParser(
                        SafeText.strip(Graphemes.fit(markdown, MAX_LENGTH, Graphemes.ELLIPSIS)), mentions, maxDepth)
                .parse();
    }
}
