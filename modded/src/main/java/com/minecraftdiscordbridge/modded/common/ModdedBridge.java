package com.minecraftdiscordbridge.modded.common;

import com.minecraftdiscordbridge.core.Bridge;
import com.minecraftdiscordbridge.core.platform.GameCommand;
import com.minecraftdiscordbridge.core.platform.GameEvent;
import com.minecraftdiscordbridge.core.platform.JoinDecision;
import com.minecraftdiscordbridge.core.platform.PlayerProfile;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.Command;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;
//? if >=1.21.11 {
import net.minecraft.server.players.NameAndId;
//?}
//? if >=1.21 {
import net.minecraft.advancements.AdvancementHolder;
//?} else
/*import net.minecraft.advancements.Advancement;*/

public final class ModdedBridge {
    public static final String MOD_ID = "minecraftdiscordbridge";
    private static final long GATE_WAIT_SECONDS = 4;
    private static final String REFUSED = "You cannot join this server right now.";

    private final LoaderHooks loader;
    private final AtomicReference<@Nullable Session> session = new AtomicReference<>();
    private final RateLimitedWarning failures = new RateLimitedWarning();

    public ModdedBridge(LoaderHooks loader) {
        this.loader = loader;
    }

    public void serverStarted(MinecraftServer server) {
        guard("server start", () -> {
            Bridge bridge = Bridge.create(new ModdedPlatform(server, loader));
            session.set(new Session(server, bridge));
            bridge.start();
            bridge.publish(new GameEvent.ServerStarted(onlinePlayers(server)));
        });
    }

    public void serverStopping() {
        stop(new GameEvent.ServerStopping());
    }

    public void serverStopped() {
        stop(new GameEvent.ServerStopped());
    }

    public void playerJoined(ServerPlayer player) {
        publish("join", player, (profile, registries) -> new GameEvent.PlayerJoined(profile, false));
    }

    public void playerLeft(ServerPlayer player) {
        publish("leave", player, (profile, registries) -> new GameEvent.PlayerLeft(profile, false));
    }

    public void playerChatted(ServerPlayer player, String message) {
        publish("chat", player, (profile, registries) -> new GameEvent.PlayerChatted(profile, message));
    }

    public void playerDied(ServerPlayer player) {
        publish(
                "death",
                player,
                (profile, registries) -> VanillaRules.showDeathMessages(player)
                        ? new GameEvent.PlayerDied(
                                profile,
                                Texts.richText(player.getCombatTracker().getDeathMessage(), registries))
                        : null);
    }

    //? if >=1.21 {
    public void advancementEarned(ServerPlayer player, AdvancementHolder advancement) {
        advancementEarned(player, () -> Advancements.announcement(advancement, player));
    }
    //?} else {
    /*public void advancementEarned(ServerPlayer player, Advancement advancement) {
        advancementEarned(player, () -> Advancements.announcement(advancement, player));
    }
    *///?}

    public CompletableFuture<Optional<Component>> checkJoin(GameProfile profile) {
        Session current = session.get();
        if (current == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        MinecraftServer server = current.server();
        try {
            return current.bridge()
                    .checkJoin(Profiles.of(profile), isOperator(server, profile))
                    .completeOnTimeout(new JoinDecision.Allow(), GATE_WAIT_SECONDS, TimeUnit.SECONDS)
                    .thenApplyAsync(decision -> kickMessage(decision, server), server);
        } catch (RuntimeException | LinkageError error) {
            failures.warn("Could not check a joining player with Minecraft Discord Bridge; report this bug", error);
            return CompletableFuture.completedFuture(Optional.empty());
        }
    }

    public boolean canUse(CommandSourceStack source, GameCommand command) {
        Session current = session.get();
        return current != null && current.bridge().canUse(ServerCommandSource.of(source, loader), command);
    }

    public int command(CommandSourceStack source, GameCommand command) {
        Session current = session.get();
        if (current == null) {
            return 0;
        }
        current.bridge().command(ServerCommandSource.of(source, loader), command);
        return Command.SINGLE_SUCCESS;
    }

    public int help(CommandSourceStack source) {
        Session current = session.get();
        if (current == null) {
            return 0;
        }
        current.bridge().help(ServerCommandSource.of(source, loader));
        return Command.SINGLE_SUCCESS;
    }

    private void advancementEarned(ServerPlayer player, Supplier<Optional<Advancements.Announcement>> announcement) {
        publish(
                "advancement",
                player,
                (profile, registries) -> announcement
                        .get()
                        .map(earned -> new GameEvent.AdvancementEarned(
                                profile, Texts.richText(earned.title(), registries), earned.frame()))
                        .orElse(null));
    }

    private void stop(GameEvent event) {
        Session stopped = session.getAndSet(null);
        if (stopped != null) {
            guard("server stop", () -> {
                stopped.bridge().publish(event);
                stopped.bridge().stop();
            });
        }
    }

    private void publish(String event, ServerPlayer player, EventFactory factory) {
        Session current = session.get();
        if (current == null || loader.isFakePlayer(player)) {
            return;
        }
        guard(event, () -> {
            GameEvent created =
                    factory.create(Profiles.of(player), current.server().registryAccess());
            if (created != null) {
                current.bridge().publish(created);
            }
        });
    }

    private List<PlayerProfile> onlinePlayers(MinecraftServer server) {
        return server.getPlayerList().getPlayers().stream()
                .filter(player -> !loader.isFakePlayer(player))
                .map(Profiles::of)
                .toList();
    }

    private void guard(String event, Runnable task) {
        try {
            task.run();
        } catch (RuntimeException | LinkageError error) {
            failures.warn(
                    "Could not hand a " + event
                            + " event to Minecraft Discord Bridge; report this bug with the error below",
                    error);
        }
    }

    private static Optional<Component> kickMessage(JoinDecision decision, MinecraftServer server) {
        if (decision instanceof JoinDecision.Deny deny) {
            return Optional.of(
                    Texts.parse(deny.json(), server.registryAccess()).orElseGet(() -> Component.literal(REFUSED)));
        }
        return Optional.empty();
    }

    //? if >=1.21.11 {
    private static boolean isOperator(MinecraftServer server, GameProfile profile) {
        return server.getPlayerList().isOp(new NameAndId(profile));
    }
    //?} else {
    /*private static boolean isOperator(MinecraftServer server, GameProfile profile) {
        return server.getPlayerList().isOp(profile);
    }
    *///?}

    private interface EventFactory {
        @Nullable
        GameEvent create(PlayerProfile player, RegistryAccess registries);
    }

    private record Session(MinecraftServer server, Bridge bridge) {}
}
