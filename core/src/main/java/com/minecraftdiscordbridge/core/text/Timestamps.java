package com.minecraftdiscordbridge.core.text;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

final class Timestamps {
    static final String STYLES = "tTdDfFR";
    private static final long EARLIEST = LocalDate.of(1, 1, 1).atStartOfDay().toEpochSecond(ZoneOffset.UTC);
    private static final long LATEST = LocalDate.of(10_000, 1, 1).atStartOfDay().toEpochSecond(ZoneOffset.UTC) - 1;
    private static final DateTimeFormatter SHORT_TIME = pattern("HH:mm 'UTC'");
    private static final DateTimeFormatter LONG_TIME = pattern("HH:mm:ss 'UTC'");
    private static final DateTimeFormatter SHORT_DATE = pattern("uuuu-MM-dd");
    private static final DateTimeFormatter LONG_DATE = pattern("d MMMM uuuu");
    private static final DateTimeFormatter SHORT_DATE_TIME = pattern("d MMMM uuuu HH:mm 'UTC'");
    private static final DateTimeFormatter LONG_DATE_TIME = pattern("EEEE, d MMMM uuuu HH:mm 'UTC'");

    private Timestamps() {}

    static @Nullable String format(long epochSecond, char style) {
        if (epochSecond < EARLIEST || epochSecond > LATEST) {
            return null;
        }
        ZonedDateTime time = Instant.ofEpochSecond(epochSecond).atZone(ZoneOffset.UTC);
        return switch (style) {
            case 't' -> SHORT_TIME.format(time);
            case 'T' -> LONG_TIME.format(time);
            case 'd' -> SHORT_DATE.format(time);
            case 'D' -> LONG_DATE.format(time);
            case 'F' -> LONG_DATE_TIME.format(time);
            default -> SHORT_DATE_TIME.format(time);
        };
    }

    private static DateTimeFormatter pattern(String pattern) {
        return DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH);
    }
}
