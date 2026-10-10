package com.minecraftdiscordbridge.core.config;

import java.util.List;

final class ConfigWriter {
    private static final String SECTION_INDENT = "  ";

    private ConfigWriter() {}

    static String render(ConfigValues values) {
        StringBuilder out = new StringBuilder();
        comment(out, "", ConfigSchema.HEADER);
        out.append('\n');
        comment(out, "", ConfigSchema.VERSION_COMMENT);
        out.append(ConfigSchema.VERSION_KEY)
                .append(": ")
                .append(ConfigSchema.VERSION)
                .append('\n');
        for (Option<?> option : ConfigSchema.OPTIONS) {
            if (option.section().isEmpty()) {
                out.append('\n');
                writeOption(out, "", option, values);
            }
        }
        for (Section section : ConfigSchema.SECTIONS) {
            out.append('\n');
            comment(out, "", section.comment());
            out.append(section.name()).append(":\n");
            boolean first = true;
            for (Option<?> option : ConfigSchema.OPTIONS) {
                if (option.section().equals(section.name())) {
                    if (!first) {
                        out.append('\n');
                    }
                    first = false;
                    writeOption(out, SECTION_INDENT, option, values);
                }
            }
        }
        return out.toString();
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
