package com.minecraftdiscordbridge.modded.common;

import com.minecraftdiscordbridge.core.platform.ActionOutcome;
import com.minecraftdiscordbridge.core.platform.GameAction;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.UserWhiteList;
import net.minecraft.server.players.UserWhiteListEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
//? if >=1.21.11 {
import net.minecraft.server.players.NameAndId;
import net.minecraft.util.Util;
//?} else {
/*import com.mojang.authlib.GameProfile;
import net.minecraft.Util;
import net.minecraft.server.players.GameProfileCache;
*///?}

final class GameActions {
    private static final Logger LOGGER = LoggerFactory.getLogger("MinecraftDiscordBridge");

    private final MinecraftServer server;

    GameActions(MinecraftServer server) {
        this.server = server;
    }

    CompletableFuture<ActionOutcome> perform(GameAction action) {
        return start(action).exceptionally(error -> failed(action, error));
    }

    private CompletableFuture<ActionOutcome> start(GameAction action) {
        if (action instanceof GameAction.WhitelistAdd add) {
            return whitelistAdd(add.playerName());
        }
        if (action instanceof GameAction.WhitelistRemove remove) {
            return whitelistRemove(remove.playerName());
        }
        if (action instanceof GameAction.Kick kick) {
            return kick(kick.playerName(), kick.reason());
        }
        if (action instanceof GameAction.RunConsoleCommand run) {
            return runConsoleCommand(run.command());
        }
        return CompletableFuture.completedFuture(new ActionOutcome.Failed("Unsupported action"));
    }

    private CompletableFuture<ActionOutcome> whitelistAdd(String playerName) {
        return resolve(playerName).thenCompose(found -> ServerThread.supply(server, () -> {
            if (found.isEmpty()) {
                return new ActionOutcome.NoSuchPlayer(playerName);
            }
            UserWhiteList whitelist = server.getPlayerList().getWhiteList();
            if (whitelist.isWhiteListed(found.get())) {
                return new ActionOutcome.Unchanged();
            }
            whitelist.add(new UserWhiteListEntry(found.get()));
            return new ActionOutcome.Done();
        }));
    }

    private CompletableFuture<ActionOutcome> whitelistRemove(String playerName) {
        return resolve(playerName).thenCompose(found -> ServerThread.supply(server, () -> {
            if (found.isEmpty()) {
                return new ActionOutcome.NoSuchPlayer(playerName);
            }
            UserWhiteList whitelist = server.getPlayerList().getWhiteList();
            if (!whitelist.isWhiteListed(found.get())) {
                return new ActionOutcome.Unchanged();
            }
            whitelist.remove(found.get());
            return new ActionOutcome.Done();
        }));
    }

    private CompletableFuture<ActionOutcome> kick(String playerName, String reason) {
        return ServerThread.supply(server, () -> {
            ServerPlayer player = server.getPlayerList().getPlayerByName(playerName);
            if (player == null) {
                return new ActionOutcome.NoSuchPlayer(playerName);
            }
            player.connection.disconnect(Component.literal(reason));
            return new ActionOutcome.Done();
        });
    }

    private CompletableFuture<ActionOutcome> runConsoleCommand(String command) {
        return ServerThread.supply(server, () -> {
            CommandFeedback feedback = new CommandFeedback();
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSource(feedback), command);
            return new ActionOutcome.Output(feedback.text());
        });
    }

    //? if >=1.21.11 {
    private CompletableFuture<Optional<NameAndId>> resolve(String playerName) {
        return CompletableFuture.supplyAsync(() -> server.services().nameToIdCache().get(playerName), Util.ioPool());
    }
    //?} else {
    /*private CompletableFuture<Optional<GameProfile>> resolve(String playerName) {
        GameProfileCache cache = server.getProfileCache();
        if (cache == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("The server has no player profile cache"));
        }
        return CompletableFuture.supplyAsync(() -> cache.get(playerName), Util.ioPool());
    }
    *///?}

    private static ActionOutcome failed(GameAction action, Throwable error) {
        Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
        LOGGER.warn("Could not perform {}; check that the server can reach the Mojang API", action, cause);
        return new ActionOutcome.Failed(Objects.toString(cause.getMessage(), cause.getClass().getSimpleName()));
    }
}
