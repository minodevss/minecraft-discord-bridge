package com.minecraftdiscordbridge.core.commands;

import com.minecraftdiscordbridge.core.chat.ChatToggle;
import com.minecraftdiscordbridge.core.i18n.Replies;
import com.minecraftdiscordbridge.core.linking.Linking;
import com.minecraftdiscordbridge.core.platform.CommandSource;
import com.minecraftdiscordbridge.core.platform.GameCommand;
import com.minecraftdiscordbridge.core.setup.ChannelSetup;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

public final class BridgeCommand {
    public interface Callbacks {
        void status(CommandSource source, Replies replies);

        void reload(CommandSource source, Replies replies);
    }

    private record Features(Linking linking, ChatToggle toggle, ChannelSetup setup) {}

    private final Replies replies;
    private final Callbacks callbacks;
    private final @Nullable Features features;

    private BridgeCommand(Replies replies, Callbacks callbacks, @Nullable Features features) {
        this.replies = replies;
        this.callbacks = callbacks;
        this.features = features;
    }

    public static BridgeCommand idle(Replies replies, Callbacks callbacks) {
        return new BridgeCommand(replies, callbacks, null);
    }

    public static BridgeCommand active(
            Replies replies, Callbacks callbacks, Linking linking, ChatToggle toggle, ChannelSetup setup) {
        return new BridgeCommand(replies, callbacks, new Features(linking, toggle, setup));
    }

    public void run(CommandSource source, GameCommand command, boolean permitted) {
        if (!permitted) {
            replies.send(source, "game.no-permission");
            return;
        }
        switch (command) {
            case STATUS -> callbacks.status(source, replies);
            case RELOAD -> callbacks.reload(source, replies);
            case LINK -> whenActive(source, active -> asPlayer(source, active.linking()::linkInGame));
            case UNLINK -> whenActive(source, active -> asPlayer(source, active.linking()::unlinkInGame));
            case TOGGLE -> whenActive(source, active -> asPlayer(source, active.toggle()::toggle));
            case SETUP -> whenActive(source, active -> active.setup().issueCode(source));
        }
    }

    public void help(CommandSource source, Set<GameCommand> permitted, boolean linkingEnabled) {
        replies.send(source, "game.help.header");
        for (GameCommand command : GameCommand.values()) {
            if (permitted.contains(command) && command.available(linkingEnabled)) {
                replies.send(source, "game.help." + command.literal());
            }
        }
    }

    private void whenActive(CommandSource source, Consumer<Features> action) {
        Features active = features;
        if (active == null) {
            replies.send(source, "game.not-ready");
        } else {
            action.accept(active);
        }
    }

    private void asPlayer(CommandSource source, BiConsumer<CommandSource, UUID> action) {
        UUID player = source.playerId();
        if (player == null) {
            replies.send(source, "game.no-permission");
        } else {
            action.accept(source, player);
        }
    }
}
