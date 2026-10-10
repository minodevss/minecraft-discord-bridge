package com.minecraftdiscordbridge.core.text;

public final class ArgumentIndex {
    public static final int NONE = -1;

    private ArgumentIndex() {}

    public static int parse(String digits) {
        if (digits.isEmpty() || !digits.chars().allMatch(Character::isDigit)) {
            return NONE;
        }
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException tooLarge) {
            return NONE;
        }
    }
}
