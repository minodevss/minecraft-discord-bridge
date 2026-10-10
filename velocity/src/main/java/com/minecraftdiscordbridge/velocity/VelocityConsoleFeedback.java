package com.minecraftdiscordbridge.velocity;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.permission.Tristate;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.kyori.adventure.audience.MessageType;
import net.kyori.adventure.identity.Identity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

final class VelocityConsoleFeedback implements CommandSource {
    private final List<String> lines = new CopyOnWriteArrayList<>();

    boolean hasFeedback() {
        return !lines.isEmpty();
    }

    String feedback() {
        return String.join("\n", lines);
    }

    @Override
    public Tristate getPermissionValue(String permission) {
        return Tristate.TRUE;
    }

    // Every Adventure sendMessage overload ends in this deprecated one, so it sees all feedback.
    @Override
    @SuppressWarnings("deprecation")
    public void sendMessage(Identity source, Component message, MessageType type) {
        lines.add(PlainTextComponentSerializer.plainText().serialize(message));
    }
}
