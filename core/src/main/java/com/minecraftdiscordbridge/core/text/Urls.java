package com.minecraftdiscordbridge.core.text;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

final class Urls {
    static final int MAX_LENGTH = 2048;
    private static final String TRAILING_PUNCTUATION = ".,:;\"')]";

    private Urls() {}

    static int bareUrlEnd(String text, int start, int end) {
        int body = schemeEnd(text, start, end);
        if (body < 0) {
            return -1;
        }
        int run = body;
        while (run < end && !isTerminator(text.charAt(run))) {
            run++;
        }
        int urlEnd = trimTrailingPunctuation(text, body, run);
        return urlEnd > body ? urlEnd : -1;
    }

    static boolean isTrailingPunctuation(char c) {
        return TRAILING_PUNCTUATION.indexOf(c) >= 0;
    }

    static boolean hasHttpScheme(String text, int start, int end) {
        return schemeEnd(text, start, end) >= 0;
    }

    static boolean isClickable(String url) {
        if (url.length() > MAX_LENGTH) {
            return false;
        }
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            if (scheme == null || uri.getHost() == null) {
                return false;
            }
            String lower = scheme.toLowerCase(Locale.ROOT);
            return lower.equals("http") || lower.equals("https");
        } catch (URISyntaxException invalid) {
            return false;
        }
    }

    private static int schemeEnd(String text, int start, int end) {
        if (startsWith(text, start, end, "https://")) {
            return start + "https://".length();
        }
        if (startsWith(text, start, end, "http://")) {
            return start + "http://".length();
        }
        return -1;
    }

    private static boolean startsWith(String text, int start, int end, String prefix) {
        return end - start >= prefix.length() && text.startsWith(prefix, start);
    }

    private static boolean isTerminator(char c) {
        return c == '<' || c == '\\' || Chars.isWhitespace(c);
    }

    private static int trimTrailingPunctuation(String text, int body, int end) {
        int unclosed = 0;
        for (int i = body; i < end; i++) {
            char c = text.charAt(i);
            if (c == '(') {
                unclosed++;
            } else if (c == ')') {
                unclosed--;
            }
        }
        int urlEnd = end;
        while (urlEnd > body) {
            char last = text.charAt(urlEnd - 1);
            if (last == ')') {
                if (unclosed >= 0) {
                    break;
                }
                unclosed++;
            } else if (!isTrailingPunctuation(last)) {
                break;
            }
            urlEnd--;
        }
        return urlEnd;
    }
}
