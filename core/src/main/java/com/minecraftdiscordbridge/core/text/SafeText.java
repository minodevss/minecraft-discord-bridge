package com.minecraftdiscordbridge.core.text;

public final class SafeText {
    private SafeText() {}

    public static String strip(String text) {
        int first = firstUnsafe(text);
        if (first < 0) {
            return text;
        }
        StringBuilder out = new StringBuilder(text.length());
        out.append(text, 0, first);
        for (int i = first; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!isUnsafe(c)) {
                out.append(c);
            }
        }
        return out.toString();
    }

    static boolean isUnsafe(char c) {
        if (c == '\n' || c == '\t') {
            return false;
        }
        return Character.getType(c) == Character.CONTROL
                || (c >= 0x202A && c <= 0x202E)
                || (c >= 0x2066 && c <= 0x2069);
    }

    private static int firstUnsafe(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (isUnsafe(text.charAt(i))) {
                return i;
            }
        }
        return -1;
    }
}
