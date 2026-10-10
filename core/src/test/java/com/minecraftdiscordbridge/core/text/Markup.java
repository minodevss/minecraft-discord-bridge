package com.minecraftdiscordbridge.core.text;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;

public final class Markup {
    private Markup() {}

    public static String of(Component component) {
        return MiniMessage.miniMessage().serialize(component).replace("<br>", "\n");
    }

    public static String plain(Component component) {
        return TextRenderer.plain(component);
    }

    public static List<ClickEvent> clickEvents(Component component) {
        List<ClickEvent> events = new ArrayList<>();
        collect(component, events);
        return events;
    }

    private static void collect(Component component, List<ClickEvent> events) {
        ClickEvent click = component.clickEvent();
        if (click != null) {
            events.add(click);
        }
        HoverEvent<?> hover = component.hoverEvent();
        if (hover != null && hover.value() instanceof Component shown) {
            collect(shown, events);
        }
        for (Component child : component.children()) {
            collect(child, events);
        }
    }
}
