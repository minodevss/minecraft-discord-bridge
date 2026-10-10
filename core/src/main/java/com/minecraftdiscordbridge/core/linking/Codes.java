package com.minecraftdiscordbridge.core.linking;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.random.RandomGenerator;

public final class Codes {
    public static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    public static final int LENGTH = 8;

    private final RandomGenerator random;

    public Codes(RandomGenerator random) {
        this.random = random;
    }

    public static Codes secure() {
        return new Codes(new SecureRandom());
    }

    public String next() {
        StringBuilder code = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }

    public static String normalize(String typed) {
        StringBuilder code = new StringBuilder(LENGTH);
        typed.codePoints().filter(c -> c != ' ' && c != '-').forEach(code::appendCodePoint);
        return code.toString().toUpperCase(Locale.ROOT);
    }
}
