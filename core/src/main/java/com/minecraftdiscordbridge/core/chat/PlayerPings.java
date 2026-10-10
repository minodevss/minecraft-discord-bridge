package com.minecraftdiscordbridge.core.chat;

import com.minecraftdiscordbridge.core.discord.DiscordId;
import com.minecraftdiscordbridge.core.storage.Link;
import com.minecraftdiscordbridge.core.text.markdown.DiscordEscaper;
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

    record Pinged(String text, Set<DiscordId> userIds) {
        Pinged {
            userIds = Set.copyOf(userIds);
        }
    }

    static Pinged escape(String message, List<Link> links) {
        if (message.indexOf('@') < 0 || links.isEmpty()) {
            return new Pinged(DiscordEscaper.escape(message), Set.of());
        }
        Map<String, DiscordId> byName = byName(links);
        Set<DiscordId> pinged = new LinkedHashSet<>();
        Matcher mention = MENTION.matcher(message).useTransparentBounds(true);
        String text = DiscordEscaper.escape(message, (source, start, out) -> {
            if (source.charAt(start) != '@') {
                return DiscordEscaper.Verbatim.NONE;
            }
            mention.region(start, source.length());
            if (!mention.lookingAt()) {
                return DiscordEscaper.Verbatim.NONE;
            }
            DiscordId userId = byName.get(mention.group(1).toLowerCase(Locale.ROOT));
            if (userId == null || (!pinged.contains(userId) && pinged.size() >= MAX_PINGS)) {
                return DiscordEscaper.Verbatim.NONE;
            }
            out.append("<@").append(userId).append('>');
            pinged.add(userId);
            return mention.end();
        });
        return new Pinged(text, pinged);
    }

    private static Map<String, DiscordId> byName(List<Link> links) {
        Map<String, DiscordId> byName = new HashMap<>();
        for (Link link : links) {
            byName.put(link.minecraftName().toLowerCase(Locale.ROOT), link.discordId());
        }
        return byName;
    }
}
