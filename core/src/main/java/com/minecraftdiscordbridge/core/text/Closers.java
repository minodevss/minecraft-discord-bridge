package com.minecraftdiscordbridge.core.text;

import static com.minecraftdiscordbridge.core.text.MarkdownText.NONE;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.function.IntPredicate;

final class Closers {
    enum Delimiter {
        FENCE,
        BOLD,
        UNDERLINE,
        STRIKE,
        SPOILER,
        STAR,
        UNDERSCORE,
        CLOSE_BRACKET,
        OPEN_BRACKET
    }

    private static final int CACHED_BACKTICK_RUNS = 8;

    private final MarkdownText text;
    private final int start;
    private final int end;
    private final Map<Delimiter, Search> delimiterSearches = new EnumMap<>(Delimiter.class);
    private final Map<Integer, Search> backtickSearches = new HashMap<>();

    Closers(MarkdownText text, int start, int end) {
        this.text = text;
        this.start = start;
        this.end = end;
    }

    int start() {
        return start;
    }

    int end() {
        return end;
    }

    int find(Delimiter delimiter, int from) {
        return delimiterSearches
                .computeIfAbsent(delimiter, unused -> new Search(j -> closes(delimiter, j)))
                .find(from);
    }

    int backticks(int run, int from) {
        IntPredicate closes = j -> closesBackticks(j, run);
        if (run > CACHED_BACKTICK_RUNS) {
            return scan(from, closes);
        }
        return backtickSearches
                .computeIfAbsent(run, unused -> new Search(closes))
                .find(from);
    }

    private int scan(int from, IntPredicate closes) {
        for (int j = from; j < end; j++) {
            if (closes.test(j)) {
                return j;
            }
        }
        return NONE;
    }

    private boolean closes(Delimiter delimiter, int j) {
        return switch (delimiter) {
            case FENCE -> text.startsWith(j, end, "```");
            case BOLD -> doubled(j, '*') && (j + 2 == end || text.at(j + 2) != '*');
            case UNDERLINE -> doubled(j, '_') && (j + 2 == end || text.at(j + 2) != '_');
            case STRIKE -> doubled(j, '~');
            case SPOILER -> doubled(j, '|');
            case STAR -> closesStar(j);
            case UNDERSCORE -> closesUnderscore(j);
            case CLOSE_BRACKET -> text.unescaped(j, ']');
            case OPEN_BRACKET -> text.unescaped(j, '[');
        };
    }

    private boolean doubled(int j, char c) {
        return j + 2 <= end && text.unescaped(j, c) && text.at(j + 1) == c;
    }

    private boolean closesStar(int j) {
        return j > start
                && text.unescaped(j, '*')
                && !Chars.isWhitespace(text.at(j - 1))
                && !text.unescaped(j - 1, '*')
                && (j + 1 == end || text.at(j + 1) != '*');
    }

    private boolean closesUnderscore(int j) {
        return j > start
                && text.unescaped(j, '_')
                && !text.unescaped(j - 1, '_')
                && (j + 1 == end || !Chars.isWordChar(text.at(j + 1)));
    }

    private boolean closesBackticks(int j, int run) {
        return text.at(j) == '`' && (j == start || text.at(j - 1) != '`') && text.runLength(j, end, '`') == run;
    }

    private final class Search {
        private final IntPredicate closes;
        private int searchedFrom = Integer.MAX_VALUE;
        private int found = NONE;

        Search(IntPredicate closes) {
            this.closes = closes;
        }

        int find(int from) {
            // The first closer at or after searchedFrom is also the first one at or after any position up to it.
            if (searchedFrom <= from && (found == NONE || found >= from)) {
                return found;
            }
            searchedFrom = from;
            found = scan(from, closes);
            return found;
        }
    }
}
