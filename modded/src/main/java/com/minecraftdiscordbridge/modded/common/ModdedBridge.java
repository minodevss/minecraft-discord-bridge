package com.minecraftdiscordbridge.modded.common;

import com.minecraftdiscordbridge.core.Bridge;
import com.minecraftdiscordbridge.core.platform.GameEvent;
import com.minecraftdiscordbridge.core.platform.PlayerProfile;
import com.mojang.brigadier.Command;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;
//? if >=1.21 {
import net.minecraft.advancements.AdvancementHolder;
//?} else
/*import net.minecraft.advancements.Advancement;*/

public final class ModdedBridge {
    public static final String MOD_ID = "minecraftdiscordbridge";

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
        publish("death", player, (profile, registries) -> VanillaRules.showDeathMessages(player)
                ? new GameEvent.PlayerDied(
                        profile, Texts.richText(player.getCombatTracker().getDeathMessage(), registries))
                : null);
    }

    public void advancementEarned(
            ServerPlayer player,
            //? if >=1.21 {
            AdvancementHolder advancement
            //?} else {
            /*Advancement advancement
            *///?}
    ) {
        advancementEarned(player, () -> Advancements.announcement(advancement, player));
    }

    public int command(CommandSourceStack source, String arguments) {
        Session current = session.get();
        if (current == null) {
            return 0;
        }
        current.bridge().command(ServerCommandSource.of(source, loader), split(arguments));
        return Command.SINGLE_SUCCESS;
    }

    private void advancementEarned(ServerPlayer player, Supplier<Optional<Advancements.Announcement>> announcement) {
        publish("advancement", player, (profile, registries) -> announcement
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
            GameEvent created = factory.create(Profiles.of(player), current.server().registryAccess());
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
                    "Could not hand a " + event + " event to Minecraft Discord Bridge; report this bug with the error below",
                    error);
        }
    }

    private static List<String> split(String arguments) {
        String trimmed = arguments.trim();
        return trimmed.isEmpty() ? List.of() : List.of(trimmed.split("\\s+"));
    }

    private interface EventFactory {
        @Nullable
        GameEvent create(PlayerProfile player, RegistryAccess registries);
    }

    private record Session(MinecraftServer server, Bridge bridge) {}
}
