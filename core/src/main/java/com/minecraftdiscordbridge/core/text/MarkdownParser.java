package com.minecraftdiscordbridge.core.text;

import static com.minecraftdiscordbridge.core.text.MarkdownText.NONE;

import com.minecraftdiscordbridge.core.text.Closers.Delimiter;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import org.jspecify.annotations.Nullable;

final class MarkdownParser {
    private static final int MAX_HEADING_LEVEL = 3;
    private static final int MAX_LIST_NUMBER_DIGITS = 9;
    private static final String LANGUAGE_SYMBOLS = "_+-.#";

    private final MarkdownText text;
    private final DiscordTokens tokens;
    private final int maxDepth;

    MarkdownParser(String source, MentionResolver mentions, int maxDepth) {
        this.text = new MarkdownText(source);
        this.tokens = new DiscordTokens(text, mentions);
        this.maxDepth = maxDepth;
    }

    Component parse() {
        return MarkdownComponents.join(parseRange(0, text.length(), Scope.ROOT));
    }

    private List<Component> parseRange(int start, int end, Scope scope) {
        ComponentBuffer out = new ComponentBuffer();
        if (scope.depth() >= maxDepth) {
            out.text(text.text(), start, end);
            return out.finish();
        }
        Closers closers = new Closers(text, start, end);
        int i = start;
        while (i < end) {
            if (scope.block() != Block.NONE && (i == start || text.at(i - 1) == '\n')) {
                i = lineStart(i, closers, scope, out);
                if (i >= end) {
                    break;
                }
            }
            i = inline(i, closers, scope, out);
        }
        return out.finish();
    }

    private int lineStart(int i, Closers closers, Scope scope, ComponentBuffer out) {
        int end = closers.end();
        if (scope.block() == Block.QUOTE) {
            if (i != closers.start()) {
                out.add(MarkdownComponents.QUOTE_PREFIX);
            }
        } else {
            int quote = quote(i, end, scope, out);
            if (quote != NONE) {
                return quote;
            }
        }
        int heading = heading(i, end, scope, out);
        if (heading != NONE) {
            return heading;
        }
        int subtext = subtext(i, end, scope, out);
        if (subtext != NONE) {
            return subtext;
        }
        return listMarker(i, end, out);
    }

    private int quote(int i, int end, Scope scope, ComponentBuffer out) {
        int p = text.skipSpaces(i, end);
        if (text.startsWith(p, end, ">>> ")) {
            out.add(MarkdownComponents.QUOTE_PREFIX);
            out.addAll(parseRange(p + 4, end, scope.quote()));
            return end;
        }
        if (text.startsWith(p, end, "> ")) {
            int lineEnd = text.lineEnd(p + 2, end);
            out.add(MarkdownComponents.QUOTE_PREFIX);
            out.addAll(parseRange(p + 2, lineEnd, scope.quote()));
            return lineEnd;
        }
        return NONE;
    }

    private int heading(int i, int end, Scope scope, ComponentBuffer out) {
        int level = text.runLength(i, end, '#');
        if (level < 1 || level > MAX_HEADING_LEVEL || !text.startsWith(i + level, end, " ")) {
            return NONE;
        }
        return lineBlock(text.skipSpaces(i + level, end), end, scope, out, Style.style(TextDecoration.BOLD));
    }

    private int subtext(int i, int end, Scope scope, ComponentBuffer out) {
        if (!text.startsWith(i, end, "-# ")) {
            return NONE;
        }
        return lineBlock(text.skipSpaces(i + 3, end), end, scope, out, Style.style(NamedTextColor.GRAY));
    }

    private int lineBlock(int contentStart, int end, Scope scope, ComponentBuffer out, Style style) {
        int lineEnd = text.lineEnd(contentStart, end);
        if (text.isBlank(contentStart, lineEnd)) {
            return NONE;
        }
        out.add(MarkdownComponents.styled(parseRange(contentStart, lineEnd, scope.inline()), style));
        return lineEnd;
    }

    private int listMarker(int i, int end, ComponentBuffer out) {
        int p = text.skipSpaces(i, end);
        int markerEnd = NONE;
        if (text.startsWith(p, end, "- ") || text.startsWith(p, end, "* ")) {
            markerEnd = p + 2;
        } else {
            int digitsEnd = text.digitsEnd(p, end, 1, MAX_LIST_NUMBER_DIGITS);
            if (digitsEnd != NONE && text.startsWith(digitsEnd, end, ". ")) {
                markerEnd = digitsEnd + 2;
            }
        }
        if (markerEnd == NONE) {
            return i;
        }
        out.text(text.text(), i, markerEnd);
        return markerEnd;
    }

    private int inline(int i, Closers closers, Scope scope, ComponentBuffer out) {
        char c = text.at(i);
        int next = switch (c) {
            case '`' -> code(i, closers, out);
            case '\\' -> escape(i, closers.end(), out);
            case '|' -> spoiler(i, closers, scope, out);
            case '*' -> star(i, closers, scope, out);
            case '_' -> underscore(i, closers, scope, out);
            case '~' -> paired(i, closers, scope, out, Delimiter.STRIKE, TextDecoration.STRIKETHROUGH);
            case '[' -> scope.links() ? maskedLink(i, closers, scope, out) : NONE;
            case '<' -> discordToken(i, closers.end(), scope, out);
            case 'h' -> scope.links() ? bareUrl(i, closers.end(), out) : NONE;
            default -> NONE;
        };
        if (next != NONE) {
            return next;
        }
        out.text(c);
        return i + 1;
    }

    private int code(int i, Closers closers, ComponentBuffer out) {
        int run = text.runLength(i, closers.end(), '`');
        if (run >= 3) {
            int block = codeBlock(i, closers, out);
            if (block != NONE) {
                return block;
            }
        }
        int contentStart = i + run;
        int close = closers.backticks(run, contentStart);
        if (close == NONE) {
            out.text(text.text(), i, contentStart);
            return contentStart;
        }
        out.add(MarkdownComponents.code(text.slice(contentStart, close)));
        return close + run;
    }

    private int codeBlock(int i, Closers closers, ComponentBuffer out) {
        int end = closers.end();
        int afterFence = i + 3;
        int languageEnd = afterFence;
        while (languageEnd < end && isLanguageChar(text.at(languageEnd))) {
            languageEnd++;
        }
        if (languageEnd > afterFence && text.startsWith(languageEnd, end, "\n")) {
            int block = codeBlockContent(text.skipNewlines(languageEnd, end), closers, out);
            if (block != NONE) {
                return block;
            }
        }
        return codeBlockContent(text.skipNewlines(afterFence, end), closers, out);
    }

    private int codeBlockContent(int contentStart, Closers closers, ComponentBuffer out) {
        if (contentStart >= closers.end()) {
            return NONE;
        }
        int close = closers.find(Delimiter.FENCE, contentStart + 1);
        if (close == NONE) {
            return NONE;
        }
        int contentEnd = close;
        while (contentEnd > contentStart + 1 && text.at(contentEnd - 1) == '\n') {
            contentEnd--;
        }
        out.add(MarkdownComponents.code(text.slice(contentStart, contentEnd)));
        return close + 3;
    }

    private int escape(int i, int end, ComponentBuffer out) {
        if (i + 1 >= end || !Chars.isEscapable(text.at(i + 1))) {
            return NONE;
        }
        out.text(text.at(i + 1));
        return i + 2;
    }

    private int spoiler(int i, Closers closers, Scope scope, ComponentBuffer out) {
        if (!text.startsWith(i, closers.end(), "||")) {
            return NONE;
        }
        int close = closers.find(Delimiter.SPOILER, i + 3);
        if (close == NONE) {
            return NONE;
        }
        out.add(MarkdownComponents.spoiler(parseRange(i + 2, close, scope.inline())));
        return close + 2;
    }

    private int star(int i, Closers closers, Scope scope, ComponentBuffer out) {
        int bold = paired(i, closers, scope, out, Delimiter.BOLD, TextDecoration.BOLD);
        if (bold != NONE || i + 1 >= closers.end()) {
            return bold;
        }
        char next = text.at(i + 1);
        if (next == '*' || Chars.isWhitespace(next)) {
            return NONE;
        }
        return italic(i, closers, scope, out, Delimiter.STAR);
    }

    private int underscore(int i, Closers closers, Scope scope, ComponentBuffer out) {
        int underline = paired(i, closers, scope, out, Delimiter.UNDERLINE, TextDecoration.UNDERLINED);
        if (underline != NONE || i + 1 >= closers.end() || text.at(i + 1) == '_') {
            return underline;
        }
        if (i > closers.start() && Chars.isWordChar(text.at(i - 1))) {
            return NONE;
        }
        return italic(i, closers, scope, out, Delimiter.UNDERSCORE);
    }

    private int paired(
            int i, Closers closers, Scope scope, ComponentBuffer out, Delimiter delimiter, TextDecoration decoration) {
        if (i + 1 >= closers.end() || text.at(i + 1) != text.at(i)) {
            return NONE;
        }
        int close = closers.find(delimiter, i + 3);
        if (close == NONE) {
            return NONE;
        }
        out.add(MarkdownComponents.decorated(parseRange(i + 2, close, scope.inline()), decoration));
        return close + 2;
    }

    private int italic(int i, Closers closers, Scope scope, ComponentBuffer out, Delimiter delimiter) {
        int close = closers.find(delimiter, i + 2);
        if (close == NONE) {
            return NONE;
        }
        out.add(MarkdownComponents.decorated(parseRange(i + 1, close, scope.inline()), TextDecoration.ITALIC));
        return close + 1;
    }

    private int maskedLink(int i, Closers closers, Scope scope, ComponentBuffer out) {
        int end = closers.end();
        int close = closers.find(Delimiter.CLOSE_BRACKET, i + 1);
        if (close == NONE || !text.startsWith(close + 1, end, "(") || text.isBlank(i + 1, close)) {
            return NONE;
        }
        int nested = closers.find(Delimiter.OPEN_BRACKET, i + 1);
        if (nested != NONE && nested < close) {
            return NONE;
        }
        LinkTarget target = linkTarget(close + 2, end);
        if (target == null || !Urls.isClickable(target.url())) {
            return NONE;
        }
        out.add(MarkdownComponents.link(parseRange(i + 1, close, scope.linkText()), target.url()));
        return target.next();
    }

    private @Nullable LinkTarget linkTarget(int open, int end) {
        int p = text.skipSpaces(open, end);
        boolean angled = text.startsWith(p, end, "<");
        if (angled) {
            p++;
        }
        int urlStart = p;
        int depth = 0;
        while (p < end && p - urlStart <= Urls.MAX_LENGTH) {
            char c = text.at(p);
            if (Chars.isWhitespace(c) || (angled && c == '>') || (c == ')' && depth == 0)) {
                break;
            }
            if (c == '(' && ++depth > 1) {
                return null;
            }
            if (c == ')') {
                depth--;
            }
            p++;
        }
        int urlEnd = p;
        if (angled) {
            if (!text.startsWith(p, end, ">")) {
                return null;
            }
            p++;
        }
        p = text.skipSpaces(p, end);
        if (urlEnd == urlStart || !text.startsWith(p, end, ")")) {
            return null;
        }
        return new LinkTarget(text.slice(urlStart, urlEnd), p + 1);
    }

    private int bareUrl(int i, int end, ComponentBuffer out) {
        int urlEnd = Urls.bareUrlEnd(text.text(), i, end);
        if (urlEnd < 0) {
            return NONE;
        }
        String url = text.slice(i, urlEnd);
        if (Urls.isClickable(url)) {
            out.add(MarkdownComponents.link(url));
        } else {
            out.text(url);
        }
        return urlEnd;
    }

    private int discordToken(int i, int end, Scope scope, ComponentBuffer out) {
        DiscordTokens.Token token = tokens.read(i, end, scope.links());
        if (token == null) {
            return NONE;
        }
        out.add(token.component());
        return token.end();
    }

    private static boolean isLanguageChar(char c) {
        return Chars.isAsciiAlphanumeric(c) || LANGUAGE_SYMBOLS.indexOf(c) >= 0;
    }

    private enum Block {
        TOP,
        QUOTE,
        NONE
    }

    private record Scope(int depth, Block block, boolean links) {
        static final Scope ROOT = new Scope(0, Block.TOP, true);

        Scope inline() {
            return new Scope(depth + 1, Block.NONE, links);
        }

        Scope quote() {
            return new Scope(depth + 1, Block.QUOTE, links);
        }

        Scope linkText() {
            return new Scope(depth + 1, Block.NONE, false);
        }
    }

    private record LinkTarget(String url, int next) {}
}
