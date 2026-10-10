package com.minecraftdiscordbridge.core;

import com.minecraftdiscordbridge.core.access.AccessMemory;
import com.minecraftdiscordbridge.core.access.JoinGate;
import com.minecraftdiscordbridge.core.access.PermissionGroups;
import com.minecraftdiscordbridge.core.actions.Actions;
import com.minecraftdiscordbridge.core.codes.Attempts;
import com.minecraftdiscordbridge.core.codes.Codes;
import com.minecraftdiscordbridge.core.config.ConfigLoader;
import com.minecraftdiscordbridge.core.discord.Discord;
import com.minecraftdiscordbridge.core.discord.OutboundMessage;
import com.minecraftdiscordbridge.core.discord.jda.JdaDiscord;
import com.minecraftdiscordbridge.core.linking.PendingLinks;
import com.minecraftdiscordbridge.core.loop.CoreLoop;
import com.minecraftdiscordbridge.core.loop.IoExecutor;
import com.minecraftdiscordbridge.core.loop.Threads;
import com.minecraftdiscordbridge.core.platform.CommandSource;
import com.minecraftdiscordbridge.core.platform.GameEvent;
import com.minecraftdiscordbridge.core.platform.JoinDecision;
import com.minecraftdiscordbridge.core.platform.Permission;
import com.minecraftdiscordbridge.core.platform.Platform;
import com.minecraftdiscordbridge.core.platform.PlayerProfile;
import com.minecraftdiscordbridge.core.setup.SetupCodes;
import com.minecraftdiscordbridge.core.status.Roster;
import com.minecraftdiscordbridge.core.storage.ShutdownMarker;
import com.minecraftdiscordbridge.core.vanilla.Fetcher;
import com.minecraftdiscordbridge.core.vanilla.HttpFetcher;
import com.minecraftdiscordbridge.core.vanilla.LanguageFiles;
import com.minecraftdiscordbridge.core.vanilla.VanillaTranslations;
import java.time.Duration;
import java.time.InstantSource;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Bridge {
    static final Duration STOP_BUDGET = Duration.ofSeconds(3);
    static final Duration DISCORD_STOP_BUDGET = Duration.ofMillis(2500);
    private static final Logger LOGGER = LoggerFactory.getLogger("MinecraftDiscordBridge");

    private final CoreLoop loop;
    private final IoExecutor io;
    private final IoExecutor downloads;
    private final ExecutorService lifecycle;
    private final Engine engine;
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean stopped = new AtomicBoolean();
    private volatile boolean stopping;
    private volatile boolean crashed;

    Bridge(
            Platform platform,
            DiscordFactory discordFactory,
            Map<String, String> environment,
            InstantSource clock,
            Codes codes,
            Duration actionTimeout,
            Fetcher fetcher,
            PermissionGroups groups) {
        this.loop = new CoreLoop();
        this.io = IoExecutor.forLoop(loop, "I/O");
        this.downloads = IoExecutor.forLoop(loop, "Downloads");
        this.lifecycle = Executors.newSingleThreadExecutor(Threads.named("Reconnect"));
        Shared shared = new Shared(
                platform,
                loop,
                io,
                clock,
                new DiscordHandle(OfflineDiscord.connecting(OfflineDiscord.STARTING)),
                new Roster(),
                new PendingLinks(codes),
                new SetupCodes(codes),
                Attempts.standard(),
                Attempts.standard(),
                actionTimeout,
                new VanillaTranslations(
                        new LanguageFiles(platform.dataDirectory(), fetcher),
                        downloads,
                        platform.info().minecraftVersion()),
                groups,
                new AccessMemory());
        this.engine = new Engine(
                shared,
                lifecycle,
                discordFactory,
                new ConfigLoader(platform.dataDirectory(), environment),
                new ShutdownMarker(platform.dataDirectory()),
                () -> stopping);
    }

    public static Bridge create(Platform platform) {
        return new Bridge(
                platform,
                JdaDiscord::create,
                System.getenv(),
                InstantSource.system(),
                Codes.secure(),
                Actions.TIMEOUT,
                new HttpFetcher(),
                PermissionGroups.detect());
    }

    public void start() {
        if (started.compareAndSet(false, true) && !stopped.get()) {
            engine.boot();
        }
    }

    public void publish(GameEvent event) {
        if (event instanceof GameEvent.ServerStopping) {
            stopping = true;
        } else if (event instanceof GameEvent.ServerStopped) {
            crashed = !stopping;
            stopping = true;
        } else {
            loop.content(() -> engine.onGameEvent(event));
        }
    }

    public void command(CommandSource source, List<String> arguments) {
        Set<Permission> granted = EnumSet.noneOf(Permission.class);
        for (Permission permission : Permission.values()) {
            if (source.hasPermission(permission)) {
                granted.add(permission);
            }
        }
        List<String> copy = List.copyOf(arguments);
        loop.control(() -> engine.command(source, granted, copy));
    }

    public CompletableFuture<JoinDecision> checkJoin(PlayerProfile player, boolean bypass) {
        if (stopped.get()) {
            return CompletableFuture.completedFuture(engine.joinFallback(player, bypass));
        }
        CompletableFuture<JoinDecision> decision = new CompletableFuture<>();
        loop.control(() -> engine.checkJoin(player, bypass, decision));
        return decision.orTimeout(JoinGate.BUDGET.toMillis(), TimeUnit.MILLISECONDS)
                .handle((done, failure) -> done != null ? done : engine.joinFallback(player, bypass));
    }

    public void stop() {
        if (!stopped.compareAndSet(false, true)) {
            return;
        }
        long deadline = System.nanoTime() + STOP_BUDGET.toNanos();
        List<OutboundMessage> farewell = engine.farewell(crashed);
        CompletableFuture<Boolean> markerCleared = io.perform(engine::clearMarker);
        Discord discord = engine.close();
        stopDiscord(discord, farewell);
        loop.close();
        awaitMarker(markerCleared, deadline);
        io.close();
        downloads.close();
        lifecycle.shutdownNow();
    }

    private static void stopDiscord(Discord discord, List<OutboundMessage> farewell) {
        Thread stopping = Threads.named("Shutdown").newThread(() -> {
            try {
                discord.shutdown(farewell);
            } catch (RuntimeException failure) {
                LOGGER.warn("Discord did not stop cleanly: {}", failure.toString());
            }
        });
        stopping.start();
        try {
            stopping.join(DISCORD_STOP_BUDGET.toMillis());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        if (stopping.isAlive()) {
            LOGGER.warn(
                    "Discord did not stop within {} ms; the server stops without waiting for it",
                    DISCORD_STOP_BUDGET.toMillis());
        }
    }

    private static void awaitMarker(CompletableFuture<Boolean> cleared, long deadline) {
        long remaining = Math.max(0, deadline - System.nanoTime());
        try {
            cleared.get(remaining, TimeUnit.NANOSECONDS);
        } catch (ExecutionException failure) {
            LOGGER.warn(
                    "Could not delete data/running: {}. The next start will report an unclean shutdown; check that"
                            + " the data folder is writable",
                    failure.getCause() == null
                            ? failure.toString()
                            : failure.getCause().toString());
        } catch (TimeoutException late) {
            LOGGER.warn("Gave up deleting data/running during shutdown; the next start may report an unclean shutdown");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
