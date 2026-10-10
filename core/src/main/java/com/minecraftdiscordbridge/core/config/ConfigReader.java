package com.minecraftdiscordbridge.core.config;

import com.minecraftdiscordbridge.core.config.RawConfig.Entry;
import com.minecraftdiscordbridge.core.config.YamlValue.Mapping;
import com.minecraftdiscordbridge.core.config.YamlValue.Scalar;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

final class ConfigReader {
    private static final Pattern WHOLE_NUMBER = Pattern.compile("[0-9]{1,9}");

    private ConfigReader() {}

    static Optional<RawConfig> parse(String text, Problems problems) {
        return RawConfig.parse(text, ConfigSchema.OPTION_PATHS, problems);
    }

    static int version(RawConfig raw, Problems problems) {
        Optional<Entry> entry = raw.get(ConfigSchema.VERSION_KEY);
        if (entry.isEmpty()) {
            problems.warning(
                    null,
                    ConfigSchema.VERSION_KEY,
                    "missing; assuming " + ConfigSchema.VERSION + ". Add \"" + ConfigSchema.VERSION_KEY + ": "
                            + ConfigSchema.VERSION + "\" at the top of the file.");
            return ConfigSchema.VERSION;
        }
        YamlValue value = entry.get().value();
        if (value instanceof Scalar scalar
                && scalar.kind() == Scalar.Kind.INTEGER
                && WHOLE_NUMBER.matcher(scalar.text()).matches()) {
            return Integer.parseInt(scalar.text());
        }
        problems.error(value.at(), ConfigSchema.VERSION_KEY, "expected a whole number, got " + value.describe());
        return ConfigSchema.VERSION;
    }

    static ConfigValues read(RawConfig raw, Problems problems) {
        ConfigValues.Builder values = ConfigValues.builder();
        for (Option<?> option : ConfigSchema.OPTIONS) {
            readOption(option, raw, problems, values);
        }
        checkSections(raw, problems);
        warnAboutUnknownKeys(raw, problems);
        return values.build();
    }

    private static <T> void readOption(
            Option<T> option, RawConfig raw, Problems problems, ConfigValues.Builder values) {
        Optional<Entry> entry = raw.get(option.path());
        T value = entry.isPresent()
                ? option.type().read(entry.get().value(), option.path(), problems)
                : option.defaultValue();
        values.set(option, value == null ? option.defaultValue() : value);
    }

    private static void checkSections(RawConfig raw, Problems problems) {
        for (Section section : ConfigSchema.SECTIONS) {
            raw.get(section.name()).map(Entry::value).ifPresent(value -> {
                boolean empty = value instanceof Scalar scalar && scalar.isNull();
                if (!(value instanceof Mapping) && !empty) {
                    problems.error(
                            value.at(), section.name(), "expected a section of settings, got " + value.describe());
                }
            });
        }
    }

    private static void warnAboutUnknownKeys(RawConfig raw, Problems problems) {
        Set<String> known = new HashSet<>(ConfigSchema.OPTION_PATHS);
        known.add(ConfigSchema.VERSION_KEY);
        ConfigSchema.SECTIONS.forEach(section -> known.add(section.name()));
        for (String path : raw.paths()) {
            if (!known.contains(path)) {
                Location at = raw.get(path).map(Entry::keyAt).orElse(null);
                problems.warning(at, path, Suggestions.unknownKey(path, ConfigSchema.OPTION_PATHS));
            }
        }
    }
}
