package com.minecraftdiscordbridge.core;

import static com.minecraftdiscordbridge.core.log.Log.LOGGER;

import com.minecraftdiscordbridge.core.access.AccessMemory;
import com.minecraftdiscordbridge.core.access.JoinGate;
import com.minecraftdiscordbridge.core.access.PermissionGroups;
import com.minecraftdiscordbridge.core.actions.GameActions;
import com.minecraftdiscordbridge.core.codes.Attempts;
import com.minecraftdiscordbridge.core.codes.Codes;
import com.minecraftdiscordbridge.core.config.ConfigLoader;
import com.minecraftdiscordbridge.core.discord.Discord;
import com.minecraftdiscordbridge.core.discord.OfflineDiscord;
import com.minecraftdiscordbridge.core.discord.OutboundMessage;
import com.minecraftdiscordbridge.core.discord.SwappableDiscord;
import com.minecraftdiscordbridge.core.discord.jda.JdaDiscord;
import com.minecraftdiscordbridge.core.linking.PendingLinks;
import com.minecraftdiscordbridge.core.log.Failures;
import com.minecraftdiscordbridge.core.loop.CoreLoop;
import com.minecraftdiscordbridge.core.loop.Deadline;
import com.minecraftdiscordbridge.core.loop.IoExecutor;
import com.minecraftdiscordbridge.core.loop.Threads;
import com.minecraftdiscordbridge.core.platform.CommandSource;
import com.minecraftdiscordbridge.core.platform.GameCommand;
import com.minecraftdiscordbridge.core.platform.GameEvent;
import com.minecraftdiscordbridge.core.platform.JoinDecision;
import com.minecraftdiscordbridge.core.platform.Platform;
import com.minecraftdiscordbridge.core.platform.PlayerProfile;
import com.minecraftdiscordbridge.core.setup.SetupCodes;
import com.minecraftdiscordbridge.core.status.Roster;
import com.minecraftdiscordbridge.core.storage.ShutdownMarker;
import com.minecraftdiscordbridge.core.vanilla.Fetcher;
import com.minecraftdiscordbridge.core.vanilla.HttpFetcher;
import com.minecraftdiscordbridge.core.vanilla.LanguageFiles;
import com.minecraftdiscordbridge.core.vanilla.VanillaTranslations;
import java.nio.file.Path;
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

public final class Bridge {
    static final Duration STOP_BUDGET = Duration.ofSeconds(3);
    private static final Duration AFTER_DISCORD = Duration.ofMillis(500);
    static final Duration DISCORD_STOP_BUDGET = STOP_BUDGET.minus(AFTER_DISCORD);

    private final CoreLoop loop;
    private final IoExecutor io;
    private final IoExecutor downloads;
    private final ExecutorService lifecycle;
    private final Activator activator;
    private final ShutdownMarker marker;
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean stopped = new AtomicBoolean();
    private volatile boolean stopping;
    private volatile boolean crashed;

    Bridge(
            Platform platform,
            DiscordConnection.Factory discordFactory,
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
        Services services = new Services(
                platform,
                loop,
                io,
                clock,
                new SwappableDiscord(OfflineDiscord.connecting(OfflineDiscord.STARTING)),
                new Roster(),
                new PendingLinks(codes),
                new SetupCodes(codes),
                Attempts.standard(),
                Attempts.standard(),
                new GameActions(platform, loop, actionTimeout),
                new VanillaTranslations(
                        new LanguageFiles(platform.dataDirectory(), fetcher),
                        downloads,
                        platform.info().minecraftVersion()),
                groups,
                new AccessMemory());
        this.marker = new ShutdownMarker(platform.dataDirectory());
        this.activator = new Activator(
                services,
                lifecycle,
                discordFactory,
                new ConfigLoader(platform.dataDirectory(), environment),
                marker,
                () -> stopping);
    }

    public static Bridge create(Platform platform) {
        return new Bridge(
                platform,
                JdaDiscord::create,
                System.getenv(),
                InstantSource.system(),
                Codes.secure(),
                GameActions.TIMEOUT,
                new HttpFetcher(),
                PermissionGroups.detect());
    }

    public void start() {
        if (started.compareAndSet(false, true) && !stopped.get()) {
            activator.boot();
        }
    }

    public void publish(GameEvent event) {
        if (event instanceof GameEvent.ServerStopping) {
            stopping = true;
        } else if (event instanceof GameEvent.ServerStopped) {
            crashed = !stopping;
            stopping = true;
        } else {
            loop.content(() -> activator.onGameEvent(event));
        }
    }

    public boolean canUse(CommandSource source, GameCommand command) {
        return command.permits(source) && command.available(activator.linkingEnabled());
    }

    public void command(CommandSource source, GameCommand command) {
        boolean permitted = command.permits(source);
        loop.control(() -> activator.command(source, command, permitted));
    }

    public void help(CommandSource source) {
        Set<GameCommand> permitted = EnumSet.noneOf(GameCommand.class);
        for (GameCommand command : GameCommand.values()) {
            if (command.permits(source)) {
                permitted.add(command);
            }
        }
        loop.control(() -> activator.help(source, permitted));
    }

    public CompletableFuture<JoinDecision> checkJoin(PlayerProfile player, boolean bypass) {
        if (stopped.get()) {
            return CompletableFuture.completedFuture(fallback(player, bypass));
        }
        CompletableFuture<JoinDecision> decision = new CompletableFuture<>();
        loop.control(() -> activator.joins().check(player, bypass, decision));
        return decision.orTimeout(JoinGate.BUDGET.toMillis(), TimeUnit.MILLISECONDS)
                .handle((done, failure) -> done != null ? done : fallback(player, bypass));
    }

    private JoinDecision fallback(PlayerProfile player, boolean bypass) {
        try {
            return activator.joins().fallback(player, bypass);
        } catch (RuntimeException failure) {
            LOGGER.warn(
                    "Let {} join because the join check failed: {}. Report this bug",
                    player.name(),
                    Failures.describe(failure));
            return new JoinDecision.Allow();
        }
    }

    public void stop() {
        if (!stopped.compareAndSet(false, true)) {
            return;
        }
        Deadline deadline = Deadline.after(STOP_BUDGET);
        List<OutboundMessage> farewell = activator.farewell(crashed);
        CompletableFuture<Void> markerCleared = io.completeOnIo(activator::clearMarker);
        Discord discord = activator.close();
        stopDiscord(discord, farewell, deadline.minus(AFTER_DISCORD));
        loop.close();
        awaitMarker(markerCleared, marker.file(), deadline);
        io.close();
        downloads.close();
        lifecycle.shutdownNow();
    }

    private static void stopDiscord(Discord discord, List<OutboundMessage> farewell, Deadline deadline) {
        Thread stopping = Threads.named("Shutdown").newThread(() -> {
            try {
                discord.shutdown(farewell, deadline);
            } catch (RuntimeException failure) {
                LOGGER.warn("Discord did not stop cleanly: {}", Failures.describe(failure));
            }
        });
        stopping.start();
        try {
            stopping.join(Math.max(1, deadline.remaining().toMillis()));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        if (stopping.isAlive()) {
            LOGGER.warn(
                    "Discord did not stop within {} ms; the server stops without waiting for it",
                    DISCORD_STOP_BUDGET.toMillis());
        }
    }

    private static void awaitMarker(CompletableFuture<Void> cleared, Path file, Deadline deadline) {
        try {
            cleared.get(deadline.remaining().toNanos(), TimeUnit.NANOSECONDS);
        } catch (ExecutionException failure) {
            LOGGER.warn(
                    "Could not delete {}: {}. The next start will report an unclean shutdown; check that its folder is"
                            + " writable",
                    file,
                    Failures.describe(failure));
        } catch (TimeoutException late) {
            LOGGER.warn("Gave up deleting {} during shutdown; the next start may report an unclean shutdown", file);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
