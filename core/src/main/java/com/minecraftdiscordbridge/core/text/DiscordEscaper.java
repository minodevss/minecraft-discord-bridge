package com.minecraftdiscordbridge.core.text;

public final class DiscordEscaper {
    private static final String MARKDOWN = "\\*_~`|[]()<>";
    private static final String LINE_START_MARKERS = "#-+";
    private static final char ZERO_WIDTH_SPACE = (char) 0x200B;

    private DiscordEscaper() {}

    @FunctionalInterface
    public interface Verbatim {
        int NONE = -1;

        int write(String text, int start, StringBuilder out);
    }

    public static String escape(String text) {
        return escape(text, (source, start, out) -> Verbatim.NONE);
    }

    public static String escape(String text, Verbatim verbatim) {
        StringBuilder out = new StringBuilder(text.length() + 16);
        int end = text.length();
        int i = 0;
        boolean lineStart = true;
        while (i < end) {
            if (lineStart) {
                i = escapeLineStart(text, i, out);
                lineStart = false;
                continue;
            }
            int urlEnd = Urls.bareUrlEnd(text, i, end);
            if (urlEnd > i) {
                i = copyUrl(text, i, urlEnd, out);
                continue;
            }
            int written = verbatim.write(text, i, out);
            if (written > i) {
                i = written;
                continue;
            }
            char c = text.charAt(i);
            if (MARKDOWN.indexOf(c) >= 0) {
                out.append('\\');
            }
            out.append(c);
            lineStart = c == '\n';
            i++;
        }
        return out.toString();
    }

    public static String escapeCodeBlock(String text) {
        StringBuilder out = new StringBuilder(text.length() + 4);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            out.append(c);
            if (c == '`' && (i + 1 == text.length() || text.charAt(i + 1) == '`')) {
                out.append(ZERO_WIDTH_SPACE);
            }
        }
        return out.toString();
    }

    private static int escapeLineStart(String text, int start, StringBuilder out) {
        int i = start;
        while (i < text.length() && (text.charAt(i) == ' ' || text.charAt(i) == '\t')) {
            out.append(text.charAt(i));
            i++;
        }
        if (i >= text.length()) {
            return i;
        }
        char c = text.charAt(i);
        if (LINE_START_MARKERS.indexOf(c) >= 0) {
            out.append('\\').append(c);
            return i + 1;
        }
        int digits = i;
        while (digits < text.length() && Chars.isAsciiDigit(text.charAt(digits))) {
            digits++;
        }
        if (digits > i && digits < text.length() && text.charAt(digits) == '.') {
            out.append(text, i, digits).append("\\.");
            return digits + 1;
        }
        return i;
    }

    private static int copyUrl(String text, int start, int urlEnd, StringBuilder out) {
        int i = urlEnd;
        while (i < text.length() && Urls.isTrailingPunctuation(text.charAt(i))) {
            i++;
        }
        out.append(text, start, i);
        return i;
    }
}
