package com.minecraftdiscordbridge.core.discord;

import java.util.regex.Pattern;

final class WebhookNames {
    static final int MAX_LENGTH = 80;
    static final String FALLBACK = "Player";
    private static final Pattern RESERVED = Pattern.compile("(?i)discord|clyde");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private WebhookNames() {}

    static String sanitize(String name) {
        String visible = name.codePoints()
                .filter(WebhookNames::isVisible)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
        String collapsed = SPACES.matcher(visible).replaceAll(" ").trim();
        String neutral = RESERVED.matcher(collapsed)
                .replaceAll(
                        match -> match.group().charAt(0) + "1" + match.group().substring(2));
        String trimmed = truncate(neutral).trim();
        return trimmed.isEmpty() ? FALLBACK : trimmed;
    }

    private static boolean isVisible(int codePoint) {
        int type = Character.getType(codePoint);
        return type != Character.CONTROL && type != Character.FORMAT && type != Character.UNASSIGNED;
    }

    private static String truncate(String name) {
        if (name.length() <= MAX_LENGTH) {
            return name;
        }
        int end = Character.isHighSurrogate(name.charAt(MAX_LENGTH - 1)) ? MAX_LENGTH - 1 : MAX_LENGTH;
        return name.substring(0, end);
    }
}
