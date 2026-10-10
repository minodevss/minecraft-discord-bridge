package com.minecraftdiscordbridge.core.chat;

import static com.minecraftdiscordbridge.core.log.Log.LOGGER;

import com.minecraftdiscordbridge.core.i18n.Replies;
import com.minecraftdiscordbridge.core.loop.IoExecutor;
import com.minecraftdiscordbridge.core.platform.CommandSource;
import com.minecraftdiscordbridge.core.storage.PreferenceStore;
import java.util.UUID;

public final class ChatToggle {
    private final PreferenceStore preferences;
    private final IoExecutor io;
    private final Replies replies;

    public ChatToggle(PreferenceStore preferences, IoExecutor io, Replies replies) {
        this.preferences = preferences;
        this.io = io;
        this.replies = replies;
    }

    public void toggle(CommandSource source) {
        UUID player = source.playerId();
        if (player == null) {
            replies.send(source, "game.players-only");
            return;
        }
        boolean hide = !preferences.hidingDiscord().contains(player);
        io.run(
                () -> preferences.hideDiscord(player, hide),
                () -> replies.send(source, hide ? "game.toggle.hidden" : "game.toggle.shown"),
                failure -> {
                    LOGGER.warn(
                            "Could not save the Discord message setting for {}: {}. Check that the data folder is"
                                    + " writable",
                            source.name(),
                            failure.getMessage());
                    replies.send(source, "game.save-failed");
                });
    }
}
