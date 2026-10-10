package com.minecraftdiscordbridge.core.text;

public final class Graphemes {
    private static final int ZERO_WIDTH_JOINER = 0x200D;

    private Graphemes() {}

    public static String fit(String text, int maxLength) {
        return fit(text, maxLength, "");
    }

    public static String fit(String text, int maxLength, String ellipsis) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, boundaryAtOrBefore(text, maxLength - ellipsis.length())) + ellipsis;
    }

    static int boundaryAtOrBefore(CharSequence text, int index) {
        int end = Math.max(0, Math.min(index, text.length()));
        while (end > 0 && end < text.length() && !isBoundary(text, end)) {
            end--;
        }
        return end;
    }

    private static boolean isBoundary(CharSequence text, int index) {
        if (Character.isHighSurrogate(text.charAt(index - 1)) && Character.isLowSurrogate(text.charAt(index))) {
            return false;
        }
        int previous = Character.codePointBefore(text, index);
        int next = Character.codePointAt(text, index);
        if (previous == ZERO_WIDTH_JOINER || extendsPrevious(next)) {
            return false;
        }
        return !isRegionalIndicator(next) || regionalIndicatorsBefore(text, index) % 2 == 0;
    }

    private static boolean extendsPrevious(int codePoint) {
        int type = Character.getType(codePoint);
        return codePoint == ZERO_WIDTH_JOINER
                || type == Character.NON_SPACING_MARK
                || type == Character.ENCLOSING_MARK
                || type == Character.COMBINING_SPACING_MARK
                || between(codePoint, 0xFE00, 0xFE0F)
                || between(codePoint, 0x1F3FB, 0x1F3FF)
                || between(codePoint, 0xE0020, 0xE007F);
    }

    private static int regionalIndicatorsBefore(CharSequence text, int index) {
        int count = 0;
        int at = index;
        while (at > 0 && isRegionalIndicator(Character.codePointBefore(text, at))) {
            count++;
            at -= 2;
        }
        return count;
    }

    private static boolean isRegionalIndicator(int codePoint) {
        return between(codePoint, 0x1F1E6, 0x1F1FF);
    }

    private static boolean between(int codePoint, int first, int last) {
        return codePoint >= first && codePoint <= last;
    }
}
