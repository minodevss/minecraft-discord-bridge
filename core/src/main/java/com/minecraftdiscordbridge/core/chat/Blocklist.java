package com.minecraftdiscordbridge.core.chat;

import java.util.List;
import java.util.regex.Pattern;

final class Blocklist {
    private Blocklist() {}

    static boolean blocks(List<Pattern> patterns, String text) {
        for (Pattern pattern : patterns) {
            if (pattern.matcher(text).find()) {
                return true;
            }
        }
        return false;
    }
}
