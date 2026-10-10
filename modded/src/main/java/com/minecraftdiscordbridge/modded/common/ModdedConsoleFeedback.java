package com.minecraftdiscordbridge.modded.common;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.commands.CommandSource;
import net.minecraft.network.chat.Component;

final class ModdedConsoleFeedback implements CommandSource {
    private final List<String> lines = new CopyOnWriteArrayList<>();

    String text() {
        return String.join("\n", lines);
    }

    @Override
    public void sendSystemMessage(Component message) {
        lines.add(message.getString());
    }

    @Override
    public boolean acceptsSuccess() {
        return true;
    }

    @Override
    public boolean acceptsFailure() {
        return true;
    }

    @Override
    public boolean shouldInformAdmins() {
        return true;
    }
}
