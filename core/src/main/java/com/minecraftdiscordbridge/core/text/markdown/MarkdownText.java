package com.minecraftdiscordbridge.core.text.markdown;

final class MarkdownText {
    static final int NONE = -1;

    private final String text;
    private final boolean[] escaped;

    MarkdownText(String text) {
        this.text = text;
        this.escaped = escapedPositions(text);
    }

    String text() {
        return text;
    }

    int length() {
        return text.length();
    }

    char at(int i) {
        return text.charAt(i);
    }

    String slice(int start, int end) {
        return text.substring(start, end);
    }

    boolean unescaped(int i, char c) {
        return text.charAt(i) == c && !escaped[i];
    }

    boolean startsWith(int i, int end, String prefix) {
        return i >= 0 && end - i >= prefix.length() && text.startsWith(prefix, i);
    }

    int runLength(int i, int end, char c) {
        int p = i;
        while (p < end && text.charAt(p) == c) {
            p++;
        }
        return p - i;
    }

    int skipSpaces(int i, int end) {
        return i + runLength(i, end, ' ');
    }

    int skipNewlines(int i, int end) {
        return i + runLength(i, end, '\n');
    }

    int lineEnd(int i, int end) {
        int p = i;
        while (p < end && text.charAt(p) != '\n') {
            p++;
        }
        return p;
    }

    boolean isBlank(int start, int end) {
        for (int p = start; p < end; p++) {
            if (!Chars.isWhitespace(text.charAt(p))) {
                return false;
            }
        }
        return true;
    }

    int digitsEnd(int start, int end, int min, int max) {
        int p = start;
        while (p < end && p - start <= max && Chars.isAsciiDigit(text.charAt(p))) {
            p++;
        }
        int count = p - start;
        return count >= min && count <= max ? p : NONE;
    }

    private static boolean[] escapedPositions(String text) {
        boolean[] escaped = new boolean[text.length()];
        int backslashes = 0;
        for (int i = 0; i < text.length(); i++) {
            escaped[i] = backslashes % 2 == 1;
            backslashes = text.charAt(i) == '\\' ? backslashes + 1 : 0;
        }
        return escaped;
    }
}
