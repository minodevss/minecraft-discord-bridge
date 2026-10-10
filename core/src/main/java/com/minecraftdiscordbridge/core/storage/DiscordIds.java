package com.minecraftdiscordbridge.core.storage;

import java.util.regex.Pattern;

final class DiscordIds {
    private static final Pattern SNOWFLAKE = Pattern.compile("[0-9]{17,20}");

    private DiscordIds() {}

    static String require(String id, String what) {
        if (!SNOWFLAKE.matcher(id).matches()) {
            throw new IllegalArgumentException(what + " must be a Discord ID of 17 to 20 digits, got \"" + id + "\"");
        }
        return id;
    }
}
