package com.minecraftdiscordbridge.core.commands;

import com.minecraftdiscordbridge.core.chat.ChatToggle;
import com.minecraftdiscordbridge.core.i18n.Replies;
import com.minecraftdiscordbridge.core.linking.Linking;
import com.minecraftdiscordbridge.core.platform.CommandSource;
import com.minecraftdiscordbridge.core.platform.Permission;
import com.minecraftdiscordbridge.core.setup.ChannelSetup;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public final class BridgeCommand {
    public static final String LINK = "link";
    public static final String UNLINK = "unlink";
    public static final String TOGGLE = "toggle";
    public static final String STATUS = "status";
    public static final String RELOAD = "reload";
    public static final String SETUP = "setup";
    public static final String HELP = "help";

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

    public void run(CommandSource source, Set<Permission> granted, List<String> arguments) {
        String subcommand = arguments.isEmpty() ? "" : arguments.get(0).toLowerCase(Locale.ROOT);
        Features active = features;
        if (subcommand.equals(STATUS)) {
            if (allowed(source, granted, Permission.STATUS)) {
                callbacks.status(source, replies);
            }
        } else if (subcommand.equals(RELOAD)) {
            if (allowed(source, granted, Permission.RELOAD)) {
                callbacks.reload(source, replies);
            }
        } else if (active != null) {
            handle(active, source, granted, subcommand);
        } else if (subcommand.isEmpty() || subcommand.equals(HELP)) {
            help(source, granted);
        } else {
            replies.send(source, "game.not-ready");
        }
    }

    private void handle(Features active, CommandSource source, Set<Permission> granted, String subcommand) {
        switch (subcommand) {
            case LINK -> {
                if (allowed(source, granted, Permission.LINK)) {
                    active.linking().linkInGame(source);
                }
            }
            case UNLINK -> {
                if (allowed(source, granted, Permission.LINK)) {
                    active.linking().unlinkInGame(source);
                }
            }
            case TOGGLE -> {
                if (allowed(source, granted, Permission.TOGGLE)) {
                    active.toggle().toggle(source);
                }
            }
            case SETUP -> {
                if (!allowed(source, granted, Permission.SETUP)) {
                    return;
                }
                if (source.playerId() != null) {
                    replies.send(source, "game.setup.console-only");
                } else {
                    active.setup().issueCode(source);
                }
            }
            default -> help(source, granted);
        }
    }

    private boolean allowed(CommandSource source, Set<Permission> granted, Permission permission) {
        if (granted.contains(permission)) {
            return true;
        }
        replies.send(source, "game.no-permission");
        return false;
    }

    private void help(CommandSource source, Set<Permission> granted) {
        replies.send(source, "game.help.header");
        if (granted.contains(Permission.LINK)) {
            replies.send(source, "game.help.link");
            replies.send(source, "game.help.unlink");
        }
        if (granted.contains(Permission.TOGGLE)) {
            replies.send(source, "game.help.toggle");
        }
        if (granted.contains(Permission.STATUS)) {
            replies.send(source, "game.help.status");
        }
        if (granted.contains(Permission.RELOAD)) {
            replies.send(source, "game.help.reload");
        }
        if (granted.contains(Permission.SETUP) && source.playerId() == null) {
            replies.send(source, "game.help.setup");
        }
    }
}
