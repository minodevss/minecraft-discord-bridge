package com.minecraftdiscordbridge.core.discord;

import java.util.Optional;

public record DiscordId(long value) {
    private static final int MIN_DIGITS = 17;
    private static final int MAX_DIGITS = 20;

    public static Optional<DiscordId> parse(String text) {
        if (text.length() < MIN_DIGITS || text.length() > MAX_DIGITS || !digitsOnly(text)) {
            return Optional.empty();
        }
        try {
            return Optional.of(new DiscordId(Long.parseUnsignedLong(text)));
        } catch (NumberFormatException beyondUnsignedLong) {
            return Optional.empty();
        }
    }

    public static DiscordId require(String text, String what) {
        return parse(text)
                .orElseThrow(() -> new IllegalArgumentException(what + " must be a Discord ID of " + MIN_DIGITS + " to "
                        + MAX_DIGITS + " digits, got \"" + text + "\""));
    }

    @Override
    public String toString() {
        return Long.toUnsignedString(value);
    }

    private static boolean digitsOnly(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }
}
