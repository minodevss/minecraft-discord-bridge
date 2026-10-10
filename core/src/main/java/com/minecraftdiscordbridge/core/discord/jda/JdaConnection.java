package com.minecraftdiscordbridge.core.discord.jda;

import static com.minecraftdiscordbridge.core.log.Log.LOGGER;

import com.minecraftdiscordbridge.core.discord.DiscordId;
import com.minecraftdiscordbridge.core.discord.DiscordSettings;
import com.minecraftdiscordbridge.core.discord.DiscordStatus;
import com.minecraftdiscordbridge.core.discord.DiscordStatus.ChannelStatus;
import com.minecraftdiscordbridge.core.discord.DiscordStatus.State;
import com.minecraftdiscordbridge.core.log.Failures;
import com.minecraftdiscordbridge.core.loop.Deadline;
import com.minecraftdiscordbridge.core.loop.Threads;
import java.time.Duration;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.exceptions.InvalidTokenException;
import net.dv8tion.jda.api.requests.CloseCode;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.requests.RestConfig;
import net.dv8tion.jda.api.utils.ConcurrentSessionController;
import okhttp3.OkHttpClient;
import org.jspecify.annotations.Nullable;

final class JdaConnection {
    private static final Duration RELEASE_MARGIN = Duration.ofMillis(400);
    private static final Duration FIRST_RETRY = Duration.ofSeconds(5);
    private static final Duration MAX_RETRY = Duration.ofMinutes(5);
    private static final String PORTAL = "https://discord.com/developers/applications";
    private static final String INVALID_TOKEN = "Discord rejected the bot token. Reset it at " + PORTAL
            + " (Bot > Reset Token), put the new token in secrets.yml and run /discord reload. If you set"
            + " MCDB_DISCORD_TOKEN or MCDB_DISCORD_TOKEN_FILE instead, update it and restart the server";

    interface Callbacks {
        void onEvent(int generation, GenericEvent event);

        void onStatusChanged();

        void onSessionLost();
    }

    private record Session(JDA jda, JdaPools pools) {}

    private record Phase(State state, @Nullable String reason) {}

    private final DiscordSettings settings;
    private final JdaDiscord.Endpoints endpoints;
    private final OkHttpClient http;
    private final Callbacks callbacks;
    private final ScheduledExecutorService lifecycle =
            Executors.newSingleThreadScheduledExecutor(Threads.named("Lifecycle"));
    private final Generations generations = new Generations();
    private final Set<String> warnings = new CopyOnWriteArraySet<>();
    private final AtomicBoolean started = new AtomicBoolean();
    private final Object sessionLock = new Object();
    private volatile Phase phase = new Phase(State.CONNECTING, null);
    private volatile @Nullable Session session;
    private volatile @Nullable ApplicationInfo application;
    private volatile boolean closing;
    private volatile boolean connecting;
    private boolean privilegedIntentsDropped;
    private Duration nextRetry = FIRST_RETRY;

    JdaConnection(
            DiscordSettings settings,
            JdaDiscord.Endpoints endpoints,
            RateLimitCounter rateLimits,
            Callbacks callbacks) {
        this.settings = settings;
        this.endpoints = endpoints;
        this.http = HttpClients.create(rateLimits);
        this.callbacks = callbacks;
    }

    void start() {
        if (!started.compareAndSet(false, true) || closing) {
            return;
        }
        try {
            lifecycle.execute(this::connect);
        } catch (RejectedExecutionException alreadyStopped) {
            enter(State.STOPPED, null);
        }
    }

    @Nullable
    JDA jda() {
        Session active = session;
        return active == null ? null : active.jda();
    }

    boolean closing() {
        return closing;
    }

    boolean isCurrent(int generation) {
        return generations.isCurrent(generation);
    }

    Optional<ApplicationInfo> application() {
        return Optional.ofNullable(application);
    }

    DiscordStatus status(Map<DiscordId, ChannelStatus> channels) {
        Phase current = phase;
        JDA active = jda();
        long ping = active == null ? -1 : active.getGatewayPing();
        return new DiscordStatus(current.state(), Optional.ofNullable(current.reason()), ping, channels);
    }

    void ready() {
        enter(
                warnings.isEmpty() ? State.READY : State.DEGRADED,
                warnings.isEmpty() ? null : String.join(" ", warnings));
    }

    void degrade(String reason) {
        LOGGER.warn(reason);
        enter(State.DEGRADED, reason);
    }

    void sessionEnded(int generation, @Nullable CloseCode code, int rawCode) {
        try {
            lifecycle.execute(() -> ended(generation, code, rawCode));
        } catch (RejectedExecutionException stopping) {
            LOGGER.debug("Ignoring a Discord event after shutdown");
        }
    }

    void shutdown(Deadline deadline, Consumer<JDA> beforeLogout) {
        long budgetMillis = deadline.remaining().toMillis();
        closing = true;
        try {
            Future<?> graceful = lifecycle.submit(() -> stopGracefully(beforeLogout, deadline));
            if (connecting) {
                http.dispatcher().cancelAll();
            }
            graceful.get(remaining(deadline), TimeUnit.NANOSECONDS);
        } catch (TimeoutException late) {
            LOGGER.warn("Discord did not stop within {} ms; closing its connections now", budgetMillis);
        } catch (ExecutionException | RejectedExecutionException failure) {
            LOGGER.warn("Discord did not stop cleanly: {}", Failures.describe(failure));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } finally {
            release();
        }
    }

    private void connect() {
        if (closing) {
            return;
        }
        if (settings.token().isBlank()) {
            stop("No bot token is set. Create a bot at " + PORTAL + ", put its token in secrets.yml and run"
                    + " /discord reload");
            return;
        }
        enter(State.CONNECTING, null);
        PreflightResult result = preflight();
        if (result instanceof PreflightResult.InvalidToken) {
            stop(INVALID_TOKEN);
        } else if (result instanceof PreflightResult.Unreachable unreachable) {
            retryLater("Cannot reach Discord: " + unreachable.reason());
        } else if (result instanceof PreflightResult.Ready ready) {
            ApplicationInfo info = ready.application();
            application = info;
            boolean messageContent = info.messageContentGranted() && !privilegedIntentsDropped;
            if (!info.messageContentGranted()) {
                warn("The Message Content intent is off, so Discord messages reach Minecraft empty. Turn on"
                        + " Message Content Intent at " + info.botSettingsUrl() + " and run /discord reload");
            }
            boolean members = settings.requestMembersIntent() && info.membersGranted() && !privilegedIntentsDropped;
            if (settings.requestMembersIntent() && !members) {
                warn("The Server Members intent is off, so the join gate and role sync cannot see members. Turn on"
                        + " Server Members Intent at " + info.botSettingsUrl() + " and run /discord reload");
            }
            login(messageContent, members);
        }
    }

    private PreflightResult preflight() {
        connecting = true;
        try {
            if (closing) {
                return new PreflightResult.Unreachable("stopping");
            }
            return new Preflight(http, endpoints.restBaseUrl())
                    .run(settings.token(), settings.autoEnableMessageContent());
        } finally {
            connecting = false;
        }
    }

    private void login(boolean messageContent, boolean members) {
        if (closing) {
            return;
        }
        int generation = generations.next();
        JdaPools pools = JdaPools.create();
        connecting = true;
        try {
            JDA jda =
                    builder(generation, intents(messageContent, members), pools).build();
            synchronized (sessionLock) {
                if (!closing) {
                    session = new Session(jda, pools);
                    nextRetry = FIRST_RETRY;
                    return;
                }
            }
            jda.shutdownNow();
            pools.shutdownNow();
        } catch (InvalidTokenException invalid) {
            pools.shutdownNow();
            stop(INVALID_TOKEN);
        } catch (RuntimeException failure) {
            pools.shutdownNow();
            retryLater("Could not log in to Discord: " + Failures.describe(failure));
        } finally {
            connecting = false;
        }
    }

    private static EnumSet<GatewayIntent> intents(boolean messageContent, boolean members) {
        EnumSet<GatewayIntent> intents = EnumSet.of(GatewayIntent.GUILD_MESSAGES);
        if (messageContent) {
            intents.add(GatewayIntent.MESSAGE_CONTENT);
        }
        if (members) {
            intents.add(GatewayIntent.GUILD_MEMBERS);
        }
        return intents;
    }

    private JDABuilder builder(int generation, EnumSet<GatewayIntent> intents, JdaPools pools) {
        JDABuilder builder = JDABuilder.createLight(settings.token(), intents)
                .setEnableShutdownHook(false)
                .setHttpClient(http)
                .setRestConfig(new RestConfig().setBaseUrl(endpoints.restBaseUrl()))
                .setRequestTimeoutRetry(false)
                .setMaxReconnectDelay((int) MAX_RETRY.toSeconds())
                .addEventListeners(new GatewayListener(generation, generations, callbacks::onEvent));
        endpoints
                .gatewayUrl()
                .ifPresent(gateway -> builder.setSessionController(new ConcurrentSessionController() {
                    @Override
                    public String getGateway() {
                        return gateway;
                    }
                }));
        return pools.applyTo(builder);
    }

    private void ended(int generation, @Nullable CloseCode code, int rawCode) {
        if (closing || !generations.isCurrent(generation)) {
            return;
        }
        releaseSession();
        callbacks.onSessionLost();
        String botSettings = application().map(ApplicationInfo::botSettingsUrl).orElse(PORTAL);
        if (code == CloseCode.DISALLOWED_INTENTS && !privilegedIntentsDropped) {
            privilegedIntentsDropped = true;
            warn("Discord refused the bot's privileged intents, so Discord messages reach Minecraft empty. Turn on"
                    + " Message Content Intent, and Server Members Intent if you use the join gate or role sync, at "
                    + botSettings + " and run /discord reload");
            enter(State.CONNECTING, null);
            login(false, false);
            return;
        }
        if (code == CloseCode.AUTHENTICATION_FAILED) {
            stop(INVALID_TOKEN);
            return;
        }
        String meaning = code == null ? "close code " + rawCode : code.getMeaning();
        stop("Discord ended the connection (" + meaning + "). Check the bot at " + botSettings
                + " and run /discord reload");
    }

    private void stopGracefully(Consumer<JDA> beforeLogout, Deadline deadline) {
        JDA jda = jda();
        if (jda == null) {
            return;
        }
        jda.cancelRequests();
        beforeLogout.accept(jda);
        jda.shutdown();
        try {
            if (!jda.awaitShutdown(remaining(deadline), TimeUnit.NANOSECONDS)) {
                jda.shutdownNow();
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            jda.shutdownNow();
        }
    }

    private void release() {
        generations.retireAll();
        releaseSession();
        var unused = lifecycle.shutdownNow();
        HttpClients.close(http);
        enter(State.STOPPED, null);
    }

    private void releaseSession() {
        Session ended;
        synchronized (sessionLock) {
            ended = session;
            session = null;
        }
        if (ended == null) {
            return;
        }
        if (ended.jda().getStatus() != JDA.Status.SHUTDOWN) {
            ended.jda().shutdownNow();
        }
        ended.pools().shutdownNow();
    }

    private void retryLater(String reason) {
        if (closing) {
            return;
        }
        Duration delay = nextRetry;
        nextRetry = nextRetry.multipliedBy(2).compareTo(MAX_RETRY) > 0 ? MAX_RETRY : nextRetry.multipliedBy(2);
        LOGGER.warn(
                "{}. Trying again in {} seconds; check that the server can reach discord.com",
                reason,
                delay.toSeconds());
        enter(State.CONNECTING, reason);
        try {
            var unused = lifecycle.schedule(this::connect, delay.toMillis(), TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException stopping) {
            enter(State.STOPPED, null);
        }
    }

    private void stop(String reason) {
        LOGGER.error(reason);
        releaseSession();
        callbacks.onSessionLost();
        enter(State.STOPPED, reason);
    }

    private void warn(String warning) {
        if (warnings.add(warning)) {
            LOGGER.warn(warning);
        }
    }

    private void enter(State state, @Nullable String reason) {
        Phase next = new Phase(state, reason);
        Phase previous;
        synchronized (sessionLock) {
            previous = phase;
            phase = next;
        }
        if (!next.equals(previous)) {
            callbacks.onStatusChanged();
        }
    }

    // JDA treats a zero awaitShutdown timeout as no limit.
    private static long remaining(Deadline deadline) {
        return Math.max(1, deadline.remaining().minus(RELEASE_MARGIN).toNanos());
    }
}
