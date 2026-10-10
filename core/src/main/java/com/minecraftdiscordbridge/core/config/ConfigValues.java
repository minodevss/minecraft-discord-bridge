package com.minecraftdiscordbridge.core.config;

import java.util.HashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

final class ConfigValues {
    private final Map<String, Object> byPath;

    private ConfigValues(Map<String, Object> byPath) {
        this.byPath = Map.copyOf(byPath);
    }

    static Builder builder() {
        return new Builder();
    }

    static ConfigValues defaults() {
        return builder().setDefaultsForAll().build();
    }

    @SuppressWarnings("unchecked")
    <T> T get(Option<T> option) {
        Object value = byPath.get(option.path());
        if (value == null) {
            throw new IllegalStateException("No value for " + option.path());
        }
        return (T) value;
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return other instanceof ConfigValues that && byPath.equals(that.byPath);
    }

    @Override
    public int hashCode() {
        return byPath.hashCode();
    }

    @Override
    public String toString() {
        return "ConfigValues" + byPath;
    }

    static final class Builder {
        private final Map<String, Object> values = new HashMap<>();

        <T> Builder set(Option<T> option, T value) {
            values.put(option.path(), value);
            return this;
        }

        Builder setDefaultsForAll() {
            ConfigSchema.OPTIONS.forEach(this::setDefault);
            return this;
        }

        private <T> void setDefault(Option<T> option) {
            set(option, option.defaultValue());
        }

        ConfigValues build() {
            return new ConfigValues(values);
        }
    }
}
