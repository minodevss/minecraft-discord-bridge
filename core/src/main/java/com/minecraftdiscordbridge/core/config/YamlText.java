package com.minecraftdiscordbridge.core.config;

import java.util.Locale;

final class YamlText {
    private static final int PREVIEW_LENGTH = 40;

    private YamlText() {}

    static String quote(String text) {
        StringBuilder quoted = new StringBuilder(text.length() + 2).append('"');
        text.codePoints().forEach(codePoint -> appendEscaped(quoted, codePoint));
        return quoted.append('"').toString();
    }

    static String preview(String text) {
        if (text.codePointCount(0, text.length()) <= PREVIEW_LENGTH) {
            return quote(text);
        }
        return quote(text.substring(0, text.offsetByCodePoints(0, PREVIEW_LENGTH)) + "...");
    }

    private static void appendEscaped(StringBuilder out, int codePoint) {
        switch (codePoint) {
            case '"' -> out.append("\\\"");
            case '\\' -> out.append("\\\\");
            case '\n' -> out.append("\\n");
            case '\r' -> out.append("\\r");
            case '\t' -> out.append("\\t");
            default -> {
                if (printable(codePoint)) {
                    out.appendCodePoint(codePoint);
                } else if (codePoint <= 0xFF) {
                    out.append(String.format(Locale.ROOT, "\\x%02X", codePoint));
                } else if (codePoint <= 0xFFFF) {
                    out.append(String.format(Locale.ROOT, "\\u%04X", codePoint));
                } else {
                    out.append(String.format(Locale.ROOT, "\\U%08X", codePoint));
                }
            }
        }
    }

    private static boolean printable(int codePoint) {
        boolean visible = (codePoint >= 0x20 && codePoint <= 0x7E)
                || (codePoint >= 0xA0 && codePoint <= 0xD7FF)
                || (codePoint >= 0xE000 && codePoint <= 0xFFFD)
                || codePoint >= 0x10000;
        return visible && codePoint != 0x2028 && codePoint != 0x2029 && codePoint != 0xFEFF;
    }
}
