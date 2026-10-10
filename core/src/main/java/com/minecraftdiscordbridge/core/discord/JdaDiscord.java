package com.minecraftdiscordbridge.core.discord;

import com.minecraftdiscordbridge.core.discord.DiscordStatus.ChannelStatus;
import com.minecraftdiscordbridge.core.discord.DiscordStatus.State;
import com.minecraftdiscordbridge.core.loop.CoreLoop;
import com.minecraftdiscordbridge.core.loop.Threads;
import java.time.Duration;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.events.session.ShutdownEvent;
import net.dv8tion.jda.api.exceptions.InvalidTokenException;
import net.dv8tion.jda.api.requests.CloseCode;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.requests.RestConfig;
import net.dv8tion.jda.api.utils.ConcurrentSessionController;
import okhttp3.OkHttpClient;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class JdaDiscord implements Discord {
    static final Duration SHUTDOWN_BUDGET = Duration.ofSeconds(3);
    static final Duration AWAIT_SHUTDOWN = Duration.ofMillis(2500);
    private static final Duration RELEASE_MARGIN = Duration.ofMillis(200);
    private static final Duration FIRST_RETRY = Duration.ofSeconds(5);
    private static final Duration MAX_RETRY = Duration.ofMinutes(5);
    private static final int MAX_RECONNECT_DELAY_SECONDS = 300;
    private static final String INVALID_TOKEN = "Discord rejected the bot token. Copy a new token from"
            + " https://discord.com/developers/applications (Bot, Reset Token) into the config";
    private static final Permission[] INVITE_PERMISSIONS = {
        Permission.VIEW_CHANNEL,
        Permission.MESSAGE_SEND,
        Permission.MESSAGE_EMBED_LINKS,
        Permission.MESSAGE_HISTORY,
        Permission.MANAGE_WEBHOOKS,
        Permission.MANAGE_CHANNEL
    };
    private static final Logger LOGGER = LoggerFactory.getLogger("MinecraftDiscordBridge");

    record Endpoints(String restBaseUrl, @Nullable String gatewayUrl) {
        static final Endpoints DISCORD = new Endpoints(RestConfig.DEFAULT_BASE_URL, null);
    }

    private record Session(int generation, JDA jda, JdaPools pools) {}

    private record Phase(State state, @Nullable String reason) {}

    private final DiscordSettings settings;
    private final GuildScope scope;
    private final Endpoints endpoints;
    private final CoreLoop loop;
    private final DiscordListener listener;
    private final RateLimitCounter rateLimits = new RateLimitCounter();
    private final OkHttpClient http;
    private final ScheduledExecutorService lifecycle;
    private final Generations generations = new Generations();
    private final WebhookSender webhooks;
    private final Outbox outbox;
    private final SlashCommands commands;
    private final JdaEvents events;
    private final Appearance appearance;
    private final Set<String> warnings = new CopyOnWriteArraySet<>();
    private volatile Phase phase = new Phase(State.CONNECTING, null);
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean stopped = new AtomicBoolean();
    private final Object sessionLock = new Object();
    private volatile @Nullable Session session;
    private volatile @Nullable ApplicationInfo application;
    private volatile boolean closing;
    private volatile boolean connecting;
    private boolean privilegedIntentsDropped;
    private Duration nextRetry = FIRST_RETRY;

    JdaDiscord(
            DiscordSettings settings,
            Endpoints endpoints,
            CoreLoop loop,
            DiscordListener listener,
            WebhookRepository webhookRepository,
            CommandHashRepository commandHashes) {
        this.settings = settings;
        this.scope = new GuildScope(settings);
        this.endpoints = endpoints;
        this.loop = loop;
        this.listener = listener;
        this.http = HttpClients.create(rateLimits);
        this.lifecycle = Executors.newSingleThreadScheduledExecutor(Threads.named("Lifecycle"));
        this.webhooks = new WebhookSender(this::currentJda, webhookRepository, rateLimits, System::nanoTime);
        this.outbox = new Outbox(settings.serverAuthor(), webhooks, loop::control);
        this.commands = new SlashCommands(settings.commands(), commandHashes);
        this.events = new JdaEvents(settings, scope, loop, listener, webhooks);
        this.appearance = new Appearance(this::currentJda, System::nanoTime, this::schedule);
    }

    public static Discord create(
            DiscordSettings settings,
            CoreLoop loop,
            DiscordListener listener,
            WebhookRepository webhooks,
            CommandHashRepository commandHashes) {
        return new JdaDiscord(settings, Endpoints.DISCORD, loop, listener, webhooks, commandHashes);
    }

    @Override
    public void start() {
        if (!started.compareAndSet(false, true) || closing) {
            return;
        }
        try {
            lifecycle.execute(this::connect);
        } catch (RejectedExecutionException alreadyStopped) {
            enter(State.STOPPED, null);
        }
    }

    @Override
    public DiscordStatus status() {
        Phase current = phase;
        Session active = session;
        Map<Long, ChannelStatus> channels = new HashMap<>();
        settings.channelIds().forEach(id -> channels.put(id, ChannelStatus.IDLE));
        channels.putAll(outbox.snapshot());
        channels.replaceAll((id, channel) -> channel.withRateLimited(rateLimits.count(id)));
        long ping = active == null ? -1 : active.jda().getGatewayPing();
        return new DiscordStatus(current.state(), current.reason(), ping, channels);
    }

    @Override
    public void send(OutboundMessage message) {
        onLoop(() -> outbox.enqueue(message));
    }

    @Override
    public void updatePresence(String text) {
        onLoop(() -> appearance.presence(text));
    }

    @Override
    public void updateTopic(long channelId, String topic) {
        onLoop(() -> appearance.topic(channelId, topic));
    }

    @Override
    public boolean isApplicationOwner(long userId) {
        ApplicationInfo info = application;
        return info != null && info.isOwner(userId);
    }

    @Override
    public void shutdown(List<OutboundMessage> finalMessages) {
        if (!stopped.compareAndSet(false, true)) {
            return;
        }
        long deadline = System.nanoTime() + SHUTDOWN_BUDGET.toNanos();
        closing = true;
        outbox.close();
        List<OutboundMessage> farewell = List.copyOf(finalMessages);
        try {
            Future<?> graceful = lifecycle.submit(() -> stopGracefully(farewell, deadline));
            if (connecting) {
                http.dispatcher().cancelAll();
            }
            graceful.get(remaining(deadline), TimeUnit.NANOSECONDS);
        } catch (TimeoutException late) {
            LOGGER.warn(
                    "Discord did not stop within {} seconds; closing its connections now", SHUTDOWN_BUDGET.toSeconds());
        } catch (ExecutionException | RejectedExecutionException failure) {
            LOGGER.warn("Discord did not stop cleanly: {}", Failures.describe(failure));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } finally {
            release();
        }
    }

    @Nullable
    JDA currentJda() {
        Session active = session;
        return active == null ? null : active.jda();
    }

    private void connect() {
        if (closing) {
            return;
        }
        if (settings.token().isBlank()) {
            stop("No bot token is set. Create a bot at https://discord.com/developers/applications and put its"
                    + " token in the config");
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
                        + " Message Content Intent at " + info.botSettingsUrl());
            }
            boolean members = settings.requestMembersIntent() && info.membersGranted() && !privilegedIntentsDropped;
            if (settings.requestMembersIntent() && !members) {
                warn("The Server Members intent is off. Turn on Server Members Intent at " + info.botSettingsUrl());
            }
            startSession(messageContent, members);
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

    private void startSession(boolean messageContent, boolean members) {
        if (closing) {
            return;
        }
        int generation = generations.next();
        JdaPools pools = JdaPools.create();
        EnumSet<GatewayIntent> intents = EnumSet.of(GatewayIntent.GUILD_MESSAGES);
        if (messageContent) {
            intents.add(GatewayIntent.MESSAGE_CONTENT);
        }
        if (members) {
            intents.add(GatewayIntent.GUILD_MEMBERS);
        }
        connecting = true;
        try {
            JDA jda = builder(generation, intents, pools).build();
            synchronized (sessionLock) {
                if (!closing) {
                    session = new Session(generation, jda, pools);
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

    private JDABuilder builder(int generation, EnumSet<GatewayIntent> intents, JdaPools pools) {
        JDABuilder builder = JDABuilder.createLight(settings.token(), intents)
                .setEnableShutdownHook(false)
                .setHttpClient(http)
                .setRestConfig(new RestConfig().setBaseUrl(endpoints.restBaseUrl()))
                .setRequestTimeoutRetry(false)
                .setMaxReconnectDelay(MAX_RECONNECT_DELAY_SECONDS)
                .addEventListeners(new GatewayListener(generation, generations, this::onEvent));
        String gateway = endpoints.gatewayUrl();
        if (gateway != null) {
            builder.setSessionController(new ConcurrentSessionController() {
                @Override
                public String getGateway() {
                    return gateway;
                }
            });
        }
        return pools.applyTo(builder);
    }

    private void onEvent(int generation, GenericEvent event) {
        if (closing) {
            return;
        }
        if (event instanceof MessageReceivedEvent message) {
            events.onMessage(message);
        } else if (event instanceof SlashCommandInteractionEvent command) {
            events.onCommand(command);
        } else if (event instanceof CommandAutoCompleteInteractionEvent autocomplete) {
            events.onAutocomplete(autocomplete);
        } else if (event instanceof ReadyEvent) {
            loop.control(() -> onReady(generation, event.getJDA()));
        } else if (event instanceof GuildJoinEvent joined
                && scope.mayResolveTo(joined.getGuild().getIdLong())) {
            loop.control(() -> onReady(generation, event.getJDA()));
        } else if (event instanceof ShutdownEvent ended) {
            CloseCode code = ended.getCloseCode();
            int rawCode = ended.getCode();
            onLifecycle(() -> sessionEnded(generation, code, rawCode));
        }
    }

    private void onReady(int generation, JDA jda) {
        if (closing || !generations.isCurrent(generation)) {
            return;
        }
        if (scope.waitingForSetup()) {
            readyForSetup(jda);
            return;
        }
        Guild guild = scope.resolve(jda);
        if (guild == null) {
            String reason = settings.guildId() == DiscordSettings.NO_GUILD
                    ? "The bot cannot see any of the configured channels. Check the channel IDs in the config, or"
                            + " invite the bot with " + inviteUrl(jda)
                    : "The bot is not in Discord server " + Long.toUnsignedString(settings.guildId())
                            + ". Invite it with " + inviteUrl(jda);
            LOGGER.warn(reason);
            enter(State.DEGRADED, reason);
            return;
        }
        commands.registerIfChanged(guild, jda.getSelfUser().getApplicationIdLong());
        for (long channelId : settings.channelIds()) {
            if (guild.getGuildChannelById(channelId) == null) {
                LOGGER.warn(
                        "The bot cannot see Discord channel {} in {}. Check the channel ID in the config and give the"
                                + " bot the View Channel permission",
                        Long.toUnsignedString(channelId),
                        guild.getName());
            } else {
                webhooks.prepare(channelId);
            }
        }
        appearance.reapplyPresence();
        outbox.open();
        LOGGER.info("Connected to Discord as {} in {}", jda.getSelfUser().getName(), guild.getName());
        if (warnings.isEmpty()) {
            enter(State.READY, null);
        } else {
            enter(State.DEGRADED, String.join(" ", warnings));
        }
    }

    private void readyForSetup(JDA jda) {
        List<Guild> guilds = jda.getGuilds();
        if (guilds.isEmpty()) {
            String reason = "The bot is not in any Discord server. Invite it with " + inviteUrl(jda);
            LOGGER.warn(reason);
            enter(State.DEGRADED, reason);
            return;
        }
        long applicationId = jda.getSelfUser().getApplicationIdLong();
        guilds.forEach(guild -> commands.registerIfChanged(guild, applicationId));
        appearance.reapplyPresence();
        outbox.open();
        LOGGER.info(
                "Connected to Discord as {}. Run /setup in the Discord channel that should show the chat",
                jda.getSelfUser().getName());
        enter(
                warnings.isEmpty() ? State.READY : State.DEGRADED,
                warnings.isEmpty() ? null : String.join(" ", warnings));
    }

    private static String inviteUrl(JDA jda) {
        return "https://discord.com/oauth2/authorize?client_id="
                + jda.getSelfUser().getApplicationId() + "&scope=bot+applications.commands&permissions="
                + Permission.getRaw(INVITE_PERMISSIONS);
    }

    private void sessionEnded(int generation, @Nullable CloseCode code, int rawCode) {
        if (closing || !generations.isCurrent(generation)) {
            return;
        }
        releaseSession();
        loop.control(outbox::hold);
        ApplicationInfo info = application;
        if (code == CloseCode.DISALLOWED_INTENTS && !privilegedIntentsDropped) {
            privilegedIntentsDropped = true;
            warn("Discord refused the Message Content intent, so Discord messages reach Minecraft empty. Turn on"
                    + " Message Content Intent at "
                    + (info == null ? "https://discord.com/developers/applications" : info.botSettingsUrl())
                    + " and restart the server");
            enter(State.CONNECTING, null);
            startSession(false, false);
            return;
        }
        if (code == CloseCode.AUTHENTICATION_FAILED) {
            stop(INVALID_TOKEN);
            return;
        }
        String meaning = code == null ? "close code " + rawCode : code.getMeaning();
        stop("Discord ended the connection: " + meaning + " Fix the bot settings and restart the server");
    }

    private void stopGracefully(List<OutboundMessage> farewell, long deadline) {
        Session active = session;
        if (active == null) {
            return;
        }
        JDA jda = active.jda();
        jda.cancelRequests();
        for (OutboundMessage message : farewell) {
            var unused = webhooks.sendFinal(message.channelId(), Post.of(message))
                    .exceptionally(failure -> {
                        LOGGER.warn("Could not send the last message to Discord: {}", Failures.describe(failure));
                        return null;
                    });
        }
        jda.shutdown();
        long wait = Math.min(AWAIT_SHUTDOWN.toNanos(), remaining(deadline));
        try {
            if (!jda.awaitShutdown(wait, TimeUnit.NANOSECONDS)) {
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
        LOGGER.warn("{}. Trying again in {} seconds", reason, delay.toSeconds());
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
        loop.control(outbox::hold);
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
            loop.control(() -> listener.onStatusChanged(status()));
        }
    }

    private void schedule(Duration delay, Runnable task) {
        var unused = loop.schedule(delay, task);
    }

    private void onLoop(Runnable task) {
        if (loop.inLoop()) {
            task.run();
        } else {
            loop.control(task);
        }
    }

    private void onLifecycle(Runnable task) {
        try {
            lifecycle.execute(task);
        } catch (RejectedExecutionException stopping) {
            LOGGER.debug("Ignoring a Discord event after shutdown");
        }
    }

    private static long remaining(long deadline) {
        return Math.max(0, deadline - System.nanoTime() - RELEASE_MARGIN.toNanos());
    }
}
