package com.minecraftdiscordbridge.core.text.markdown;

final class Chars {
    private Chars() {}

    static boolean isWhitespace(char c) {
        return Character.isWhitespace(c) || Character.isSpaceChar(c) || c == '﻿';
    }

    static boolean isAsciiDigit(char c) {
        return c >= '0' && c <= '9';
    }

    static boolean isAsciiAlphanumeric(char c) {
        return isAsciiDigit(c) || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    static boolean isWordChar(char c) {
        return isAsciiAlphanumeric(c) || c == '_';
    }

    static boolean isEscapable(char c) {
        return !isAsciiAlphanumeric(c) && !isWhitespace(c);
    }
}
