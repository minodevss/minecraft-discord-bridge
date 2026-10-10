package com.minecraftdiscordbridge.core.commands;

import com.minecraftdiscordbridge.core.chat.ChatToggle;
import com.minecraftdiscordbridge.core.i18n.Replies;
import com.minecraftdiscordbridge.core.linking.Linking;
import com.minecraftdiscordbridge.core.platform.CommandSource;
import com.minecraftdiscordbridge.core.platform.Permission;
import com.minecraftdiscordbridge.core.setup.ChannelSetup;
import java.util.Set;

public final class GameCommands {
    public static final String LINK = "link";
    public static final String UNLINK = "unlink";
    public static final String TOGGLE = "toggle";
    public static final String STATUS = "status";
    public static final String RELOAD = "reload";
    public static final String SETUP = "setup";

    private final Replies replies;
    private final Linking linking;
    private final ChatToggle toggle;
    private final ChannelSetup setup;

    public GameCommands(Replies replies, Linking linking, ChatToggle toggle, ChannelSetup setup) {
        this.replies = replies;
        this.linking = linking;
        this.toggle = toggle;
        this.setup = setup;
    }

    public void handle(CommandSource source, Set<Permission> granted, String subcommand) {
        switch (subcommand) {
            case LINK -> {
                if (allowed(replies, source, granted, Permission.LINK)) {
                    linking.linkInGame(source);
                }
            }
            case UNLINK -> {
                if (allowed(replies, source, granted, Permission.LINK)) {
                    linking.unlinkInGame(source);
                }
            }
            case TOGGLE -> {
                if (allowed(replies, source, granted, Permission.TOGGLE)) {
                    toggle.toggle(source);
                }
            }
            case SETUP -> {
                if (!allowed(replies, source, granted, Permission.SETUP)) {
                    return;
                }
                if (source.playerId() != null) {
                    replies.send(source, "game.setup.console-only");
                } else {
                    setup.issueCode(source);
                }
            }
            default -> help(replies, source, granted);
        }
    }

    public static boolean allowed(
            Replies replies, CommandSource source, Set<Permission> granted, Permission permission) {
        if (granted.contains(permission)) {
            return true;
        }
        replies.send(source, "game.no-permission");
        return false;
    }

    public static void help(Replies replies, CommandSource source, Set<Permission> granted) {
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
