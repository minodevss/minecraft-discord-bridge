package com.minecraftdiscordbridge.core.config;

import java.util.List;
import org.jspecify.annotations.Nullable;

final class ConfigWriter {
    private static final String SECTION_INDENT = "  ";

    private ConfigWriter() {}

    static String render(ConfigValues values) {
        StringBuilder out = new StringBuilder();
        comment(out, "", ConfigSchema.HEADER);
        for (Block block : ConfigSchema.LAYOUT) {
            out.append('\n');
            if (block instanceof Section section) {
                writeSection(out, section, values);
            } else if (block instanceof Option<?> option) {
                writeOption(out, "", option, values);
            }
        }
        out.append('\n');
        comment(out, "", ConfigSchema.VERSION_COMMENT);
        out.append(ConfigSchema.VERSION_KEY)
                .append(": ")
                .append(ConfigSchema.VERSION)
                .append('\n');
        return out.toString();
    }

    private static void writeSection(StringBuilder out, Section section, ConfigValues values) {
        comment(out, "", section.comment());
        out.append(section.name()).append(":\n");
        @Nullable Option<?> previous = null;
        for (Option<?> option : section.options()) {
            if (previous != null
                    && (!previous.comment().isEmpty() || !option.comment().isEmpty())) {
                out.append('\n');
            }
            writeOption(out, SECTION_INDENT, option, values);
            previous = option;
        }
    }

    private static <T> void writeOption(StringBuilder out, String indent, Option<T> option, ConfigValues values) {
        comment(out, indent, option.comment());
        option.type().write(out, indent, option.key(), values.get(option));
    }

    private static void comment(StringBuilder out, String indent, List<String> lines) {
        for (String line : lines) {
            out.append(indent).append(line.isEmpty() ? "#" : "# " + line).append('\n');
        }
    }
}
