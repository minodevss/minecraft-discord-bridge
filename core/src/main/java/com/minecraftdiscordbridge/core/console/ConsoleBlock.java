package com.minecraftdiscordbridge.core.console;

import com.minecraftdiscordbridge.core.discord.OutboundMessage;
import com.minecraftdiscordbridge.core.text.Graphemes;
import com.minecraftdiscordbridge.core.text.markdown.DiscordEscaper;
import java.util.Collection;

final class ConsoleBlock {
    private static final String FENCE = "```";
    static final int MAX_BODY_LENGTH = OutboundMessage.MAX_LENGTH - 2 * FENCE.length() - 2;

    private ConsoleBlock() {}

    static String body(String text, Collection<String> secrets, int maxLength) {
        String safe = DiscordEscaper.escapeCodeBlock(ConsoleRedactor.redact(text, secrets));
        return Graphemes.fit(safe, maxLength, Graphemes.ELLIPSIS);
    }

    static String fence(CharSequence body) {
        return FENCE + "\n" + body + "\n" + FENCE;
    }
}
