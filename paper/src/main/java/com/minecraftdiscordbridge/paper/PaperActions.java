package com.minecraftdiscordbridge.paper;

import com.minecraftdiscordbridge.core.platform.ActionOutcome;
import com.minecraftdiscordbridge.core.platform.GameAction;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

final class PaperActions {
    private final Plugin plugin;

    PaperActions(Plugin plugin) {
        this.plugin = plugin;
    }

    CompletableFuture<ActionOutcome> perform(GameAction action) {
        CompletableFuture<ActionOutcome> outcome = new CompletableFuture<>();
        attempt(outcome, () -> {
            switch (action) {
                case GameAction.WhitelistAdd add -> whitelist(outcome, add.playerName(), true);
                case GameAction.WhitelistRemove remove -> whitelist(outcome, remove.playerName(), false);
                case GameAction.Kick kick -> kick(outcome, kick.playerName(), kick.reason());
                case GameAction.RunConsoleCommand run -> runConsoleCommand(outcome, run.command());
            }
        });
        return outcome;
    }

    private void whitelist(CompletableFuture<ActionOutcome> outcome, String name, boolean listed) {
        Bukkit.getAsyncScheduler()
                .runNow(
                        plugin,
                        task -> attempt(outcome, () -> whitelistResolved(outcome, resolve(name), name, listed)));
    }

    private void whitelistResolved(
            CompletableFuture<ActionOutcome> outcome, @Nullable UUID id, String name, boolean listed) {
        if (id == null) {
            outcome.complete(new ActionOutcome.NoSuchPlayer(name));
            return;
        }
        Bukkit.getGlobalRegionScheduler()
                .execute(plugin, () -> attempt(outcome, () -> outcome.complete(setWhitelisted(id, name, listed))));
    }

    private static @Nullable UUID resolve(String name) {
        try {
            var profile = Bukkit.createProfile(name);
            return profile.complete(false) ? profile.getId() : null;
        } catch (IllegalArgumentException invalidName) {
            return null;
        }
    }

    private static ActionOutcome setWhitelisted(UUID id, String name, boolean listed) {
        OfflinePlayer player = offlinePlayer(id, name);
        if (player.isWhitelisted() == listed) {
            return new ActionOutcome.Unchanged();
        }
        player.setWhitelisted(listed);
        return new ActionOutcome.Done();
    }

    private static OfflinePlayer offlinePlayer(UUID id, String name) {
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        return cached != null && id.equals(cached.getUniqueId()) ? cached : Bukkit.getOfflinePlayer(id);
    }

    private void kick(CompletableFuture<ActionOutcome> outcome, String name, String reason) {
        Player player = onlinePlayer(name);
        Runnable gone = () -> outcome.complete(new ActionOutcome.NoSuchPlayer(name));
        if (player == null
                || player.getScheduler().run(plugin, task -> kickNow(outcome, player, reason), gone) == null) {
            gone.run();
        }
    }

    private static void kickNow(CompletableFuture<ActionOutcome> outcome, Player player, String reason) {
        attempt(outcome, () -> {
            player.kick(Component.text(reason));
            outcome.complete(new ActionOutcome.Done());
        });
    }

    private void runConsoleCommand(CompletableFuture<ActionOutcome> outcome, String command) {
        Bukkit.getGlobalRegionScheduler()
                .execute(plugin, () -> attempt(outcome, () -> outcome.complete(dispatch(command))));
    }

    private static ActionOutcome dispatch(String command) {
        List<String> feedback = new CopyOnWriteArrayList<>();
        CommandSender sender = Bukkit.createCommandSender(
                message -> feedback.add(PlainTextComponentSerializer.plainText().serialize(message)));
        boolean known = Bukkit.dispatchCommand(sender, command);
        if (!known && feedback.isEmpty()) {
            return new ActionOutcome.UnknownCommand();
        }
        return new ActionOutcome.Output(String.join("\n", feedback));
    }

    private static @Nullable Player onlinePlayer(String name) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().equalsIgnoreCase(name)) {
                return player;
            }
        }
        return null;
    }

    private static void attempt(CompletableFuture<ActionOutcome> outcome, Runnable work) {
        try {
            work.run();
        } catch (RuntimeException failure) {
            outcome.completeExceptionally(failure);
        }
    }
}
