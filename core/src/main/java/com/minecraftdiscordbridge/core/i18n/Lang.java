package com.minecraftdiscordbridge.core.i18n;

import com.minecraftdiscordbridge.core.text.Format;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;

public final class Lang {
    private static final Lang ENGLISH = load(Language.EN);
    private static final Lang KOREAN = load(Language.KO);

    private final Language language;
    private final Map<String, String> strings;
    private final Map<String, Format> formats = new ConcurrentHashMap<>();

    private Lang(Language language, Map<String, String> strings) {
        this.language = language;
        this.strings = Map.copyOf(strings);
    }

    public static Lang of(Language language) {
        return language == Language.KO ? KOREAN : ENGLISH;
    }

    public static Lang forDiscordLocale(String locale, Lang fallback) {
        String lower = locale.toLowerCase(Locale.ROOT);
        if (lower.startsWith("ko")) {
            return KOREAN;
        }
        if (lower.startsWith("en")) {
            return ENGLISH;
        }
        return fallback;
    }

    public Language language() {
        return language;
    }

    public String raw(String key) {
        String value = strings.get(key);
        if (value != null) {
            return value;
        }
        return ENGLISH.strings.getOrDefault(key, key);
    }

    public String text(String key, Object... arguments) {
        return fill(raw(key), arguments);
    }

    public String count(String key, long count) {
        return text(key + (count == 1 ? ".one" : ".other"), count);
    }

    public Component component(String key, Format.Slot... slots) {
        Set<String> names = Arrays.stream(slots).map(Format.Slot::key).collect(Collectors.toSet());
        String cacheKey = key + new TreeSet<>(names);
        Format format = formats.computeIfAbsent(cacheKey, ignored -> Format.compile(key, raw(key), names));
        return format.render(slots);
    }

    static String fill(String template, Object... arguments) {
        StringBuilder out = new StringBuilder(template.length() + 16);
        int i = 0;
        while (i < template.length()) {
            char c = template.charAt(i);
            int close = c == '{' ? template.indexOf('}', i) : -1;
            int index = close > i + 1 ? index(template.substring(i + 1, close)) : -1;
            if (index >= 0 && index < arguments.length) {
                out.append(arguments[index]);
                i = close + 1;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    private static int index(String digits) {
        if (digits.length() > 2 || !digits.chars().allMatch(Character::isDigit)) {
            return -1;
        }
        return Integer.parseInt(digits);
    }

    private static Lang load(Language language) {
        String resource = "/lang/" + language.name().toLowerCase(Locale.ROOT) + ".properties";
        try (InputStream stream = Lang.class.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException("Missing " + resource + " in the bridge jar");
            }
            Properties properties = new Properties();
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
            Map<String, String> strings = new HashMap<>();
            properties.stringPropertyNames().forEach(name -> strings.put(name, properties.getProperty(name)));
            return new Lang(language, strings);
        } catch (IOException failure) {
            throw new UncheckedIOException("Cannot read " + resource, failure);
        }
    }
}
