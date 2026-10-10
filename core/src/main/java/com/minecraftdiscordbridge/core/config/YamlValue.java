package com.minecraftdiscordbridge.core.config;

import java.util.List;
import java.util.Optional;

sealed interface YamlValue permits YamlValue.Scalar, YamlValue.Sequence, YamlValue.Mapping {
    Location at();

    String describe();

    record Scalar(String text, Kind kind, Location at) implements YamlValue {
        enum Kind {
            NULL,
            BOOLEAN,
            INTEGER,
            FLOAT,
            STRING
        }

        static Scalar text(String text, Location at) {
            return new Scalar(text, Kind.STRING, at);
        }

        boolean isNull() {
            return kind == Kind.NULL;
        }

        @Override
        public String describe() {
            return switch (kind) {
                case NULL -> "nothing";
                case STRING -> YamlText.preview(text);
                case BOOLEAN, INTEGER, FLOAT -> text;
            };
        }
    }

    record Sequence(List<YamlValue> items, Location at) implements YamlValue {
        public Sequence {
            items = List.copyOf(items);
        }

        @Override
        public String describe() {
            return "a list";
        }
    }

    record Mapping(List<Field> fields, Location at) implements YamlValue {
        public Mapping {
            fields = List.copyOf(fields);
        }

        Optional<Field> field(String key) {
            return fields.stream().filter(field -> field.key().equals(key)).findFirst();
        }

        @Override
        public String describe() {
            return "a map";
        }
    }

    record Field(String key, Location keyAt, YamlValue value) {}
}
