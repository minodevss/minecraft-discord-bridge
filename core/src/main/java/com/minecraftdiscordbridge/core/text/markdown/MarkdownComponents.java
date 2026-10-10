package com.minecraftdiscordbridge.core.text.markdown;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

final class MarkdownComponents {
    static final Component QUOTE_PREFIX = Component.text("▍ ", NamedTextColor.GRAY);
    private static final int SPOILER_BLOCK_LIMIT = 32;

    private MarkdownComponents() {}

    static Component code(String code) {
        return Component.text(code, NamedTextColor.GRAY);
    }

    static Component styled(List<Component> content, Style style) {
        return Component.text().append(content).style(style).build();
    }

    static Component decorated(List<Component> content, TextDecoration decoration) {
        return styled(content, Style.style(decoration));
    }

    static Component link(String url) {
        return link(List.of(Component.text(url)), url);
    }

    static Component link(List<Component> content, String url) {
        return Component.text()
                .append(content)
                .decoration(TextDecoration.UNDERLINED, true)
                .clickEvent(ClickEvent.openUrl(url))
                .hoverEvent(HoverEvent.showText(Component.text(url)))
                .build();
    }

    static Component spoiler(List<Component> content) {
        Component revealed = join(content);
        long visible = PlainTextComponentSerializer.plainText()
                .serialize(revealed)
                .codePoints()
                .filter(codePoint -> codePoint != '\n')
                .count();
        int blocks = (int) Math.max(1, Math.min(SPOILER_BLOCK_LIMIT, visible));
        return Component.text("▒".repeat(blocks), NamedTextColor.DARK_GRAY).hoverEvent(HoverEvent.showText(revealed));
    }

    static Component join(List<Component> parts) {
        if (parts.isEmpty()) {
            return Component.empty();
        }
        if (parts.size() == 1) {
            return parts.get(0);
        }
        return Component.text().append(parts).build();
    }
}
