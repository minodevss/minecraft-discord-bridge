package com.minecraftdiscordbridge.core.text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.TranslationArgument;

final class TranslationFormat {
    private static final Pattern ARGUMENT = Pattern.compile("%(?:(\\d+)\\$)?([A-Za-z%]|$)");

    private TranslationFormat() {}

    static void flatten(TranslatableComponent component, Translations translations, Consumer<Component> parts) {
        String pattern = translations.pattern(component.key());
        if (pattern == null) {
            pattern = component.fallback();
        }
        if (pattern == null) {
            pattern = component.key();
        }
        List<Component> arguments = new ArrayList<>();
        for (TranslationArgument argument : component.arguments()) {
            arguments.add(argument.asComponent());
        }
        List<Component> pieces = new ArrayList<>();
        if (!decompose(pattern, arguments, pieces)) {
            pieces.clear();
            pieces.add(Component.text(pattern));
        }
        pieces.forEach(parts);
    }

    private static boolean decompose(String pattern, List<Component> arguments, List<Component> pieces) {
        Matcher matcher = ARGUMENT.matcher(pattern);
        int literalStart = 0;
        int nextArgument = 0;
        while (matcher.find()) {
            if (!addLiteral(pattern.substring(literalStart, matcher.start()), pieces)) {
                return false;
            }
            String type = matcher.group(2);
            String explicitIndex = matcher.group(1);
            if (type.equals("%") && explicitIndex == null) {
                pieces.add(Component.text("%"));
            } else if (type.equals("s")) {
                int index = explicitIndex == null ? nextArgument++ : ArgumentIndex.parse(explicitIndex) - 1;
                if (index < 0 || index >= arguments.size()) {
                    return false;
                }
                pieces.add(arguments.get(index));
            } else {
                return false;
            }
            literalStart = matcher.end();
        }
        return addLiteral(pattern.substring(literalStart), pieces);
    }

    private static boolean addLiteral(String literal, List<Component> pieces) {
        if (literal.indexOf('%') >= 0) {
            return false;
        }
        if (!literal.isEmpty()) {
            pieces.add(Component.text(literal));
        }
        return true;
    }
}
