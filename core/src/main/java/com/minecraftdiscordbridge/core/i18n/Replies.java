package com.minecraftdiscordbridge.core.i18n;

import com.minecraftdiscordbridge.core.platform.CommandSource;
import com.minecraftdiscordbridge.core.text.Format;
import com.minecraftdiscordbridge.core.text.TextRenderer;
import net.kyori.adventure.text.Component;

public record Replies(Lang lang, int dataVersion) {
    public void send(CommandSource source, String key, Format.Slot... slots) {
        send(source, lang.component(key, slots));
    }

    public void send(CommandSource source, Component message) {
        source.reply(TextRenderer.toJson(message, dataVersion));
    }
}
