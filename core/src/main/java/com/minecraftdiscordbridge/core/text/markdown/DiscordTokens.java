package com.minecraftdiscordbridge.core.text.markdown;

import static com.minecraftdiscordbridge.core.text.markdown.MarkdownText.NONE;

import com.minecraftdiscordbridge.core.text.SafeText;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.jspecify.annotations.Nullable;

final class DiscordTokens {
    private static final int MIN_ID_DIGITS = 17;
    private static final int MAX_ID_DIGITS = 20;
    private static final int MAX_EMOJI_ID_DIGITS = 21;
    private static final int MAX_NAME_LENGTH = 32;
    private static final int MAX_COMMAND_PARTS = 3;
    private static final int MAX_TIMESTAMP_DIGITS = 18;

    private final MarkdownText text;
    private final MentionResolver mentions;

    DiscordTokens(MarkdownText text, MentionResolver mentions) {
        this.text = text;
        this.mentions = mentions;
    }

    @Nullable
    Token read(int open, int end, boolean links) {
        if (open + 2 >= end) {
            return null;
        }
        int p = open + 2;
        return switch (text.at(open + 1)) {
            case '@' -> userOrRole(p, end);
            case '#' -> channel(p, end);
            case ':' -> emoji(p, end);
            case 'a' -> text.startsWith(p, end, ":") ? emoji(p + 1, end) : null;
            case 't' -> text.startsWith(p, end, ":") ? timestamp(p + 1, end) : null;
            case '/' -> slashCommand(p, end);
            case 'h' -> links ? autolink(open + 1, end) : null;
            default -> null;
        };
    }

    private @Nullable Token userOrRole(int p, int end) {
        boolean role = text.startsWith(p, end, "&");
        int idStart = role || text.startsWith(p, end, "!") ? p + 1 : p;
        int idEnd = closedDigits(idStart, end, MAX_ID_DIGITS);
        Long id = snowflake(idStart, idEnd);
        if (id == null) {
            return null;
        }
        return new Token(role ? roleMention(id) : userMention(id), idEnd + 1);
    }

    private @Nullable Token channel(int p, int end) {
        int idEnd = closedDigits(p, end, MAX_ID_DIGITS);
        Long id = snowflake(p, idEnd);
        if (id == null) {
            return null;
        }
        String name = mentions.channelName(id)
                .map(found -> "#" + SafeText.strip(found))
                .orElse("#unknown-channel");
        return new Token(Component.text(name), idEnd + 1);
    }

    private @Nullable Token emoji(int nameStart, int end) {
        int nameEnd = nameStart;
        while (nameEnd < end && nameEnd - nameStart < MAX_NAME_LENGTH && Chars.isWordChar(text.at(nameEnd))) {
            nameEnd++;
        }
        if (nameEnd - nameStart < 2 || !text.startsWith(nameEnd, end, ":")) {
            return null;
        }
        int idEnd = closedDigits(nameEnd + 1, end, MAX_EMOJI_ID_DIGITS);
        if (idEnd == NONE) {
            return null;
        }
        return new Token(Component.text(":" + text.slice(nameStart, nameEnd) + ":"), idEnd + 1);
    }

    private @Nullable Token timestamp(int p, int end) {
        int digitsStart = text.startsWith(p, end, "-") ? p + 1 : p;
        int digitsEnd = text.digitsEnd(digitsStart, end, 1, MAX_TIMESTAMP_DIGITS);
        if (digitsEnd == NONE) {
            return null;
        }
        char style = 'f';
        int close = digitsEnd;
        if (text.startsWith(close, end, ":") && close + 1 < end && Timestamps.STYLES.indexOf(text.at(close + 1)) >= 0) {
            style = text.at(close + 1);
            close += 2;
        }
        if (!text.startsWith(close, end, ">")) {
            return null;
        }
        String formatted = Timestamps.format(Long.parseLong(text.slice(p, digitsEnd)), style);
        return formatted == null ? null : new Token(Component.text(formatted), close + 1);
    }

    private @Nullable Token slashCommand(int p, int end) {
        int nameEnd = p;
        for (int part = 1; part <= MAX_COMMAND_PARTS; part++) {
            int partEnd = nameEnd;
            while (partEnd < end && partEnd - nameEnd < MAX_NAME_LENGTH && isCommandChar(text.at(partEnd))) {
                partEnd++;
            }
            if (partEnd == nameEnd) {
                return null;
            }
            nameEnd = partEnd;
            if (part == MAX_COMMAND_PARTS || !text.startsWith(nameEnd, end, " ")) {
                break;
            }
            nameEnd++;
        }
        if (!text.startsWith(nameEnd, end, ":")) {
            return null;
        }
        int idEnd = closedDigits(nameEnd + 1, end, MAX_EMOJI_ID_DIGITS);
        if (idEnd == NONE) {
            return null;
        }
        return new Token(Component.text("/" + text.slice(p, nameEnd)), idEnd + 1);
    }

    private @Nullable Token autolink(int urlStart, int end) {
        if (!Urls.hasHttpScheme(text.text(), urlStart, end)) {
            return null;
        }
        int p = urlStart;
        while (p < end && p - urlStart <= Urls.MAX_LENGTH && text.at(p) != '>' && !Chars.isWhitespace(text.at(p))) {
            p++;
        }
        if (!text.startsWith(p, end, ">")) {
            return null;
        }
        String url = text.slice(urlStart, p);
        return Urls.isClickable(url) ? new Token(MarkdownComponents.link(url), p + 1) : null;
    }

    private Component userMention(long id) {
        return Component.text(
                mentions.userName(id).map(name -> "@" + SafeText.strip(name)).orElse("@unknown-user"));
    }

    private Component roleMention(long id) {
        Optional<MentionResolver.Role> found = mentions.role(id);
        if (found.isEmpty()) {
            return Component.text("@unknown-role");
        }
        MentionResolver.Role role = found.get();
        Component mention = Component.text("@" + SafeText.strip(role.name()));
        return role.rgb().isPresent() ? mention.color(TextColor.color(role.rgb().getAsInt())) : mention;
    }

    private int closedDigits(int start, int end, int maxDigits) {
        int digitsEnd = text.digitsEnd(start, end, MIN_ID_DIGITS, maxDigits);
        return digitsEnd != NONE && text.startsWith(digitsEnd, end, ">") ? digitsEnd : NONE;
    }

    private @Nullable Long snowflake(int start, int end) {
        if (end == NONE) {
            return null;
        }
        try {
            return Long.parseUnsignedLong(text.slice(start, end));
        } catch (NumberFormatException tooLarge) {
            return null;
        }
    }

    private static boolean isCommandChar(char c) {
        return Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == '\'';
    }

    record Token(Component component, int end) {}
}
