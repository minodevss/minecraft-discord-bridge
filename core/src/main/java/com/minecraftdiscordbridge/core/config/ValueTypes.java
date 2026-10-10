package com.minecraftdiscordbridge.core.config;

import com.minecraftdiscordbridge.core.config.YamlValue.Mapping;
import com.minecraftdiscordbridge.core.config.YamlValue.Scalar;
import com.minecraftdiscordbridge.core.config.YamlValue.Sequence;
import com.minecraftdiscordbridge.core.discord.DiscordId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.jspecify.annotations.Nullable;

final class ValueTypes {
    private static final String DISCORD_ID_TEXT = "a Discord ID (17 to 20 digits)";

    static final ValueType<Boolean> BOOLEAN = new BooleanType();
    static final ValueType<String> TEXT = new TextType("text", text -> true, false);
    static final ValueType<String> TEXT_OR_NOTHING = new TextType("text", text -> true, true);
    static final ValueType<String> NAME = new TextType("a name", text -> !text.isBlank(), false);
    static final ValueType<String> DISCORD_ID =
            new TextType(DISCORD_ID_TEXT, text -> DiscordId.parse(text).isPresent(), false);
    static final ValueType<String> DISCORD_ID_OR_EMPTY = new TextType(
            DISCORD_ID_TEXT + " or \"\"",
            text -> text.isEmpty() || DiscordId.parse(text).isPresent(),
            true);
    static final ValueType<String> REGEX = new RegexType();
    static final ValueType<RoleSyncRule> ROLE_SYNC_RULE = new RoleSyncRuleType();

    private ValueTypes() {}

    static <E extends Enum<E>> ValueType<E> choice(Class<E> type) {
        return new ChoiceType<>(List.copyOf(EnumSet.allOf(type)));
    }

    static <E> ValueType<List<E>> listOf(ValueType<E> element) {
        return new ListType<>(element);
    }

    static String id(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static String mismatch(String expectation, YamlValue value) {
        return "expected " + expectation + ", got " + value.describe();
    }

    private abstract static class ScalarType<T> implements ValueType<T> {
        abstract String render(T value);

        @Override
        public void write(StringBuilder out, String indent, String key, T value) {
            out.append(indent).append(key).append(": ").append(render(value)).append('\n');
        }

        @Override
        public void writeItem(StringBuilder out, String indent, T value) {
            out.append(indent).append("- ").append(render(value)).append('\n');
        }
    }

    private static final class BooleanType extends ScalarType<Boolean> {
        @Override
        public @Nullable Boolean read(YamlValue value, String path, Problems problems) {
            if (value instanceof Scalar scalar && scalar.kind() == Scalar.Kind.BOOLEAN) {
                return Boolean.parseBoolean(scalar.text().toLowerCase(Locale.ROOT));
            }
            problems.error(value.at(), path, mismatch("true or false", value));
            return null;
        }

        @Override
        String render(Boolean value) {
            return value.toString();
        }
    }

    private static final class TextType extends ScalarType<String> {
        private final String expectation;
        private final Predicate<String> accepts;
        private final boolean nothingIsEmpty;

        TextType(String expectation, Predicate<String> accepts, boolean nothingIsEmpty) {
            this.expectation = expectation;
            this.accepts = accepts;
            this.nothingIsEmpty = nothingIsEmpty;
        }

        @Override
        public @Nullable String read(YamlValue value, String path, Problems problems) {
            if (!(value instanceof Scalar scalar) || (scalar.isNull() && !nothingIsEmpty)) {
                problems.error(value.at(), path, mismatch(expectation, value));
                return null;
            }
            String text = scalar.isNull() ? "" : scalar.text();
            if (!accepts.test(text)) {
                problems.error(value.at(), path, mismatch(expectation, value));
                return null;
            }
            return text;
        }

        @Override
        String render(String value) {
            return YamlText.quote(value);
        }
    }

    private static final class RegexType extends ScalarType<String> {
        @Override
        public @Nullable String read(YamlValue value, String path, Problems problems) {
            String text = TEXT.read(value, path, problems);
            if (text == null) {
                return null;
            }
            try {
                Pattern.compile(text);
                return text;
            } catch (PatternSyntaxException invalid) {
                problems.error(
                        value.at(),
                        path,
                        "invalid regular expression: " + invalid.getDescription() + " near index "
                                + invalid.getIndex());
                return null;
            }
        }

        @Override
        String render(String value) {
            return YamlText.quote(value);
        }
    }

    private static final class ChoiceType<E extends Enum<E>> extends ScalarType<E> {
        private final List<E> choices;

        ChoiceType(List<E> choices) {
            this.choices = choices;
        }

        @Override
        public @Nullable E read(YamlValue value, String path, Problems problems) {
            if (value instanceof Scalar scalar && !scalar.isNull()) {
                for (E choice : choices) {
                    if (id(choice).equals(scalar.text())) {
                        return choice;
                    }
                }
            }
            problems.error(value.at(), path, mismatch(expectation(), value));
            return null;
        }

        private String expectation() {
            List<String> ids = choices.stream().map(ValueTypes::id).toList();
            String allButLast = String.join(", ", ids.subList(0, ids.size() - 1));
            return allButLast + " or " + ids.get(ids.size() - 1);
        }

        @Override
        String render(E value) {
            return YamlText.quote(id(value));
        }
    }

    private static final class ListType<E> implements ValueType<List<E>> {
        private final ValueType<E> element;

        ListType(ValueType<E> element) {
            this.element = element;
        }

        @Override
        public @Nullable List<E> read(YamlValue value, String path, Problems problems) {
            if (value instanceof Scalar scalar && scalar.isNull()) {
                return List.of();
            }
            if (!(value instanceof Sequence sequence)) {
                problems.error(value.at(), path, mismatch("a list", value));
                return null;
            }
            List<E> items = new ArrayList<>();
            boolean valid = true;
            for (int index = 0; index < sequence.items().size(); index++) {
                E item = element.read(sequence.items().get(index), path + "[" + index + "]", problems);
                if (item == null) {
                    valid = false;
                } else {
                    items.add(item);
                }
            }
            return valid ? List.copyOf(items) : null;
        }

        @Override
        public void write(StringBuilder out, String indent, String key, List<E> value) {
            if (value.isEmpty()) {
                out.append(indent).append(key).append(": []\n");
                return;
            }
            out.append(indent).append(key).append(":\n");
            value.forEach(item -> element.writeItem(out, indent + "  ", item));
        }

        @Override
        public void writeItem(StringBuilder out, String indent, List<E> value) {
            throw new UnsupportedOperationException("Lists of lists are not supported");
        }
    }

    private static final class RoleSyncRuleType implements ValueType<RoleSyncRule> {
        private static final String DISCORD_ROLE = "discord-role";
        private static final String GROUP = "group";
        private static final String DIRECTION = "direction";
        private static final List<String> KEYS = List.of(DISCORD_ROLE, GROUP, DIRECTION);
        private static final String REQUIRED_KEYS = DISCORD_ROLE + " and " + GROUP;
        private static final ValueType<SyncDirection> DIRECTIONS = choice(SyncDirection.class);

        @Override
        public @Nullable RoleSyncRule read(YamlValue value, String path, Problems problems) {
            if (!(value instanceof Mapping mapping)) {
                problems.error(value.at(), path, mismatch("a map with " + REQUIRED_KEYS, value));
                return null;
            }
            mapping.fields().stream()
                    .filter(field -> !KEYS.contains(field.key()))
                    .forEach(field -> problems.warning(
                            field.keyAt(), path + "." + field.key(), Suggestions.unknownKey(field.key(), KEYS)));
            String role = required(mapping, DISCORD_ROLE, DISCORD_ID, path, problems);
            String group = required(mapping, GROUP, NAME, path, problems);
            SyncDirection direction = mapping.field(DIRECTION)
                    .map(field -> DIRECTIONS.read(field.value(), path + "." + DIRECTION, problems))
                    .orElse(SyncDirection.DISCORD_TO_MINECRAFT);
            if (role == null || group == null || direction == null) {
                return null;
            }
            return new RoleSyncRule(DiscordId.require(role, path + "." + DISCORD_ROLE), group, direction);
        }

        private static <T> @Nullable T required(
                Mapping mapping, String key, ValueType<T> type, String path, Problems problems) {
            YamlValue.Field field = mapping.field(key).orElse(null);
            if (field == null) {
                problems.error(mapping.at(), path, "missing " + key + "; each rule needs " + REQUIRED_KEYS);
                return null;
            }
            return type.read(field.value(), path + "." + key, problems);
        }

        @Override
        public void write(StringBuilder out, String indent, String key, RoleSyncRule value) {
            out.append(indent).append(key).append(":\n");
            writeFields(out, indent + "  ", indent + "  ", value);
        }

        @Override
        public void writeItem(StringBuilder out, String indent, RoleSyncRule value) {
            writeFields(out, indent + "- ", indent + "  ", value);
        }

        private static void writeFields(StringBuilder out, String first, String rest, RoleSyncRule value) {
            out.append(first)
                    .append(DISCORD_ROLE)
                    .append(": ")
                    .append(YamlText.quote(value.discordRole().toString()))
                    .append('\n');
            out.append(rest)
                    .append(GROUP)
                    .append(": ")
                    .append(YamlText.quote(value.group()))
                    .append('\n');
            if (value.direction() != SyncDirection.DISCORD_TO_MINECRAFT) {
                out.append(rest)
                        .append(DIRECTION)
                        .append(": ")
                        .append(YamlText.quote(id(value.direction())))
                        .append('\n');
            }
        }
    }
}
