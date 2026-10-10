package com.minecraftdiscordbridge.core.chat;

import com.minecraftdiscordbridge.core.storage.Link;
import com.minecraftdiscordbridge.core.text.DiscordEscaper;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class PlayerPings {
    static final int MAX_PINGS = 3;
    private static final Pattern MENTION =
            Pattern.compile("(?<![A-Za-z0-9_])@(\\.?[A-Za-z0-9_]{1,16})(?![A-Za-z0-9_])");

    private PlayerPings() {}

    record Pinged(String text, Set<Long> userIds) {
        Pinged {
            userIds = Set.copyOf(userIds);
        }
    }

    static Pinged escape(String message, List<Link> links) {
        if (message.indexOf('@') < 0 || links.isEmpty()) {
            return new Pinged(DiscordEscaper.escape(message), Set.of());
        }
        Map<String, Long> byName = byName(links);
        Set<Long> pinged = new LinkedHashSet<>();
        StringBuilder out = new StringBuilder(message.length() + 16);
        Matcher matcher = MENTION.matcher(message);
        int copied = 0;
        while (matcher.find()) {
            Long userId = byName.get(matcher.group(1).toLowerCase(Locale.ROOT));
            if (userId == null || (!pinged.contains(userId) && pinged.size() >= MAX_PINGS)) {
                continue;
            }
            out.append(DiscordEscaper.escape(message.substring(copied, matcher.start())));
            out.append("<@").append(Long.toUnsignedString(userId)).append('>');
            pinged.add(userId);
            copied = matcher.end();
        }
        out.append(DiscordEscaper.escape(message.substring(copied)));
        return new Pinged(out.toString(), pinged);
    }

    private static Map<String, Long> byName(List<Link> links) {
        Map<String, Long> byName = new HashMap<>();
        for (Link link : links) {
            byName.put(
                    link.minecraftName().toLowerCase(Locale.ROOT),
                    link.discordId().value());
        }
        return byName;
    }
}
