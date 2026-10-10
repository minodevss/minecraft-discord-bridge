package com.minecraftdiscordbridge.core.text;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.minimessage.Context;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.ParsingException;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.ArgumentQueue;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.jspecify.annotations.Nullable;

public final class Format {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final Pattern PLACEHOLDER_NAME = Pattern.compile("[a-z0-9_-]+");
    private static final String LEGACY_CODES = "0123456789abcdefklmnor";
    private static final int HEX_CODE_LENGTH = "&#rrggbb".length();

    private final String name;
    private final String template;
    private final Set<String> placeholders;

    private Format(String name, String template, Set<String> placeholders) {
        this.name = name;
        this.template = template;
        this.placeholders = placeholders;
    }

    public static Format compile(String name, String template, Set<String> placeholders) {
        for (String placeholder : placeholders) {
            checkPlaceholderName(name, placeholder);
        }
        String sectionCode = firstLegacyCode(template, '§');
        if (sectionCode != null) {
            throw new IllegalArgumentException("Format '" + name + "' uses the legacy color code '" + sectionCode
                    + "', which MiniMessage cannot read. Use MiniMessage tags such as <green> or <bold> instead");
        }
        Set<String> problems = problems(template, placeholders);
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException("Format '" + name + "' is not valid MiniMessage: "
                    + String.join("; ", problems) + ". Available placeholders: " + describe(placeholders));
        }
        return new Format(name, template, Set.copyOf(placeholders));
    }

    public String name() {
        return name;
    }

    public Component render(Slot... slots) {
        return render(List.of(slots));
    }

    public Component render(List<Slot> slots) {
        TagResolver.Builder resolvers = TagResolver.builder();
        Set<String> filled = new HashSet<>();
        for (Slot slot : slots) {
            if (!placeholders.contains(slot.key())) {
                throw new IllegalArgumentException("Format '" + name + "' has no placeholder <" + slot.key() + ">");
            }
            resolvers.resolver(slot.resolver());
            filled.add(slot.key());
        }
        for (String placeholder : placeholders) {
            if (!filled.contains(placeholder)) {
                resolvers.resolver(Placeholder.unparsed(placeholder, ""));
            }
        }
        return MINI_MESSAGE.deserialize(template, resolvers.build());
    }

    public Optional<String> legacyCodeWarning() {
        String code = firstLegacyCode(template, '&');
        if (code == null) {
            return Optional.empty();
        }
        return Optional.of("Format '" + name + "' contains the legacy color code '" + code
                + "', which shows as plain text. Use MiniMessage tags such as <green> or <bold> instead");
    }

    private static void checkPlaceholderName(String format, String placeholder) {
        if (!PLACEHOLDER_NAME.matcher(placeholder).matches()) {
            throw new IllegalArgumentException("Placeholder <" + placeholder + "> of format '" + format
                    + "' may only use lowercase letters, digits, '_' and '-'");
        }
        if (TagResolver.standard().has(placeholder)) {
            throw new IllegalArgumentException("Placeholder <" + placeholder + "> of format '" + format
                    + "' has the same name as a MiniMessage tag; rename the placeholder");
        }
    }

    private static Set<String> problems(String template, Set<String> placeholders) {
        List<TagResolver> known = new ArrayList<>();
        known.add(TagResolver.standard());
        for (String placeholder : placeholders) {
            known.add(Placeholder.unparsed(placeholder, ""));
        }
        CheckingResolver checking = new CheckingResolver(TagResolver.resolver(known));
        Set<String> problems = new LinkedHashSet<>();
        try {
            MiniMessage.builder().tags(checking).build().deserialize(template);
        } catch (ParsingException invalid) {
            problems.add(detail(invalid));
        }
        problems.addAll(checking.problems());
        return problems;
    }

    private static @Nullable String firstLegacyCode(String template, char prefix) {
        for (int i = 0; i + 1 < template.length(); i++) {
            if (template.charAt(i) != prefix) {
                continue;
            }
            char code = template.charAt(i + 1);
            if (prefix == '&' && code == '#') {
                return template.substring(i, Math.min(i + HEX_CODE_LENGTH, template.length()));
            }
            if (LEGACY_CODES.indexOf(prefix == '§' ? Character.toLowerCase(code) : code) >= 0) {
                return template.substring(i, i + 2);
            }
        }
        return null;
    }

    private static String describe(Set<String> placeholders) {
        if (placeholders.isEmpty()) {
            return "none";
        }
        List<String> tags = new ArrayList<>();
        for (String placeholder : new TreeSet<>(placeholders)) {
            tags.add("<" + placeholder + ">");
        }
        return String.join(", ", tags);
    }

    private static String detail(ParsingException invalid) {
        String detail = invalid.detailMessage();
        return detail == null ? String.valueOf(invalid.getMessage()) : detail;
    }

    public record Slot(String key, TagResolver resolver) {
        public static Slot text(String key, String text) {
            return new Slot(key, Placeholder.unparsed(key, SafeText.strip(text)));
        }

        public static Slot component(String key, ComponentLike component) {
            return new Slot(key, Placeholder.component(key, component));
        }
    }

    private static final class CheckingResolver implements TagResolver {
        private final TagResolver known;
        private final Set<String> unknown = new LinkedHashSet<>();
        private final Map<String, String> failures = new LinkedHashMap<>();
        private final Set<String> resolved = new HashSet<>();

        CheckingResolver(TagResolver known) {
            this.known = known;
        }

        @Override
        public @Nullable Tag resolve(String name, ArgumentQueue arguments, Context context) {
            if (!known.has(name)) {
                unknown.add(name);
                return null;
            }
            try {
                Tag tag = known.resolve(name, arguments, context);
                if (tag != null) {
                    resolved.add(name);
                }
                return tag;
            } catch (ParsingException invalid) {
                failures.putIfAbsent(name, detail(invalid));
                throw invalid;
            }
        }

        @Override
        public boolean has(String name) {
            return true;
        }

        // MiniMessage also resolves close tags without arguments, so a failure only counts if the tag never resolved.
        Set<String> problems() {
            Set<String> problems = new LinkedHashSet<>();
            for (String name : unknown) {
                problems.add("unknown tag <" + name + ">");
            }
            failures.forEach((name, detail) -> {
                if (!resolved.contains(name)) {
                    problems.add("tag <" + name + "> is invalid (" + detail + ")");
                }
            });
            return problems;
        }
    }
}
