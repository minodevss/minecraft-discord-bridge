package com.minecraftdiscordbridge.core.discord.jda;

import static com.minecraftdiscordbridge.core.log.Log.LOGGER;

import com.minecraftdiscordbridge.core.discord.CommandHashRepository;
import com.minecraftdiscordbridge.core.discord.Discord;
import com.minecraftdiscordbridge.core.discord.DiscordId;
import com.minecraftdiscordbridge.core.discord.DiscordListener;
import com.minecraftdiscordbridge.core.discord.DiscordSettings;
import com.minecraftdiscordbridge.core.discord.DiscordStatus;
import com.minecraftdiscordbridge.core.discord.DiscordStatus.ChannelStatus;
import com.minecraftdiscordbridge.core.discord.DiscordStatus.State;
import com.minecraftdiscordbridge.core.discord.MemberLookup;
import com.minecraftdiscordbridge.core.discord.OutboundMessage;
import com.minecraftdiscordbridge.core.discord.RoleInfo;
import com.minecraftdiscordbridge.core.discord.WebhookRepository;
import com.minecraftdiscordbridge.core.log.Failures;
import com.minecraftdiscordbridge.core.loop.CoreLoop;
import com.minecraftdiscordbridge.core.loop.Deadline;
import com.minecraftdiscordbridge.core.loop.Threads;
import java.time.Duration;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
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
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberUpdateEvent;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.events.session.ShutdownEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.exceptions.InvalidTokenException;
import net.dv8tion.jda.api.requests.CloseCode;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.requests.RestConfig;
import net.dv8tion.jda.api.utils.ConcurrentSessionController;
import okhttp3.OkHttpClient;
import org.jspecify.annotations.Nullable;

public final class JdaDiscord implements Discord {
    private static final Duration RELEASE_MARGIN = Duration.ofMillis(200);
    private static final Duration FIRST_RETRY = Duration.ofSeconds(5);
    private static final Duration MAX_RETRY = Duration.ofMinutes(5);
    private static final int MAX_RECONNECT_DELAY_SECONDS = 300;
    private static final String PORTAL = "https://discord.com/developers/applications";
    private static final String INVALID_TOKEN = "Discord rejected the bot token. Reset it at " + PORTAL
            + " (Bot > Reset Token), put the new token in secrets.yml and run /discord reload. If you set"
            + " MCDB_DISCORD_TOKEN or MCDB_DISCORD_TOKEN_FILE instead, update it and restart the server";
    private static final Permission[] INVITE_PERMISSIONS = {
        Permission.VIEW_CHANNEL,
        Permission.MESSAGE_SEND,
        Permission.MESSAGE_EMBED_LINKS,
        Permission.MESSAGE_HISTORY,
        Permission.MANAGE_WEBHOOKS,
        Permission.MANAGE_CHANNEL
    };
    private static final String NOT_CONNECTED = "Discord is not connected";
    private static final Duration MEMBER_LOOKUP_TIMEOUT = Duration.ofSeconds(5);
    private static final String ROLE_SYNC_REASON = "Minecraft Discord Bridge role sync";
    private static final Map<Permission, String> DANGEROUS_PERMISSIONS = dangerousPermissions();

    record Endpoints(String restBaseUrl, Optional<String> gatewayUrl) {
        static final Endpoints DISCORD = new Endpoints(RestConfig.DEFAULT_BASE_URL, Optional.empty());
    }

    private record Session(JDA jda, JdaPools pools) {}

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
        this.appearance = new Appearance(this::currentJda, System::nanoTime, loop);
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
        Map<DiscordId, ChannelStatus> channels = new HashMap<>();
        settings.channelIds().forEach(id -> channels.put(id, ChannelStatus.IDLE));
        channels.putAll(outbox.snapshot());
        channels.replaceAll((id, channel) -> channel.withRateLimited(rateLimits.count(id)));
        long ping = active == null ? -1 : active.jda().getGatewayPing();
        return new DiscordStatus(current.state(), Optional.ofNullable(current.reason()), ping, channels);
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
    public void updateTopic(DiscordId channelId, String topic) {
        onLoop(() -> appearance.topic(channelId, topic));
    }

    @Override
    public boolean isApplicationOwner(DiscordId userId) {
        ApplicationInfo info = application;
        return info != null && info.isOwner(userId.value());
    }

    @Override
    public CompletableFuture<MemberLookup> member(DiscordId userId) {
        Guild guild = guild();
        if (guild == null) {
            return CompletableFuture.completedFuture(new MemberLookup.Unavailable(NOT_CONNECTED));
        }
        try {
            return guild.retrieveMemberById(userId.value())
                    .useCache(false)
                    .timeout(MEMBER_LOOKUP_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
                    .submit()
                    .handle((member, failure) ->
                            failure == null ? JdaEvents.member(member) : lookupFailed(userId, failure));
        } catch (RuntimeException failure) {
            return CompletableFuture.completedFuture(new MemberLookup.Unavailable(Failures.describe(failure)));
        }
    }

    @Override
    public Map<DiscordId, RoleInfo> roles() {
        Guild guild = guild();
        if (guild == null) {
            return Map.of();
        }
        Map<DiscordId, RoleInfo> roles = new HashMap<>();
        for (Role role : guild.getRoles()) {
            boolean assignable = !role.isManaged()
                    && !role.isPublicRole()
                    && guild.getSelfMember().canInteract(role);
            DiscordId id = new DiscordId(role.getIdLong());
            roles.put(id, new RoleInfo(id, role.getName(), dangerous(role.getPermissions()), assignable));
        }
        return Map.copyOf(roles);
    }

    @Override
    public CompletableFuture<Void> addRole(DiscordId userId, DiscordId roleId) {
        return changeRole(userId, roleId, true);
    }

    @Override
    public CompletableFuture<Void> removeRole(DiscordId userId, DiscordId roleId) {
        return changeRole(userId, roleId, false);
    }

    @Override
    public void shutdown(List<OutboundMessage> finalMessages, Deadline deadline) {
        if (!stopped.compareAndSet(false, true)) {
            return;
        }
        long budgetMillis = deadline.remaining().toMillis();
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
            LOGGER.warn("Discord did not stop within {} ms; closing its connections now", budgetMillis);
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

    private @Nullable Guild guild() {
        JDA jda = currentJda();
        return jda == null || closing ? null : scope.current(jda);
    }

    private CompletableFuture<Void> changeRole(DiscordId userId, DiscordId roleId, boolean add) {
        Guild guild = guild();
        if (guild == null) {
            return CompletableFuture.failedFuture(new IllegalStateException(NOT_CONNECTED));
        }
        Role role = guild.getRoleById(roleId.value());
        if (role == null) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Discord role " + roleId + " does not exist"));
        }
        try {
            UserSnowflake user = UserSnowflake.fromId(userId.value());
            return (add ? guild.addRoleToMember(user, role) : guild.removeRoleFromMember(user, role))
                    .reason(ROLE_SYNC_REASON)
                    .submit();
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    private static MemberLookup lookupFailed(DiscordId userId, Throwable failure) {
        Throwable cause = Failures.unwrap(failure);
        if (cause instanceof ErrorResponseException error
                && (error.getErrorResponse() == ErrorResponse.UNKNOWN_MEMBER
                        || error.getErrorResponse() == ErrorResponse.UNKNOWN_USER)) {
            return new MemberLookup.NotMember(userId);
        }
        return new MemberLookup.Unavailable(Failures.describe(failure));
    }

    private static Set<String> dangerous(Set<Permission> granted) {
        Set<String> found = new LinkedHashSet<>();
        DANGEROUS_PERMISSIONS.forEach((permission, name) -> {
            if (granted.contains(permission)) {
                found.add(name);
            }
        });
        return found;
    }

    private static Map<Permission, String> dangerousPermissions() {
        Map<Permission, String> names = new EnumMap<>(Permission.class);
        names.put(Permission.ADMINISTRATOR, "Administrator");
        names.put(Permission.MANAGE_SERVER, "Manage Server");
        names.put(Permission.MANAGE_ROLES, "Manage Roles");
        names.put(Permission.BAN_MEMBERS, "Ban Members");
        names.put(Permission.KICK_MEMBERS, "Kick Members");
        names.put(Permission.MESSAGE_MENTION_EVERYONE, "Mention @everyone");
        return names;
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

    private JDABuilder builder(int generation, EnumSet<GatewayIntent> intents, JdaPools pools) {
        JDABuilder builder = JDABuilder.createLight(settings.token(), intents)
                .setEnableShutdownHook(false)
                .setHttpClient(http)
                .setRestConfig(new RestConfig().setBaseUrl(endpoints.restBaseUrl()))
                .setRequestTimeoutRetry(false)
                .setMaxReconnectDelay(MAX_RECONNECT_DELAY_SECONDS)
                .addEventListeners(new GatewayListener(generation, generations, this::onEvent));
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
        } else if (event instanceof GuildMemberUpdateEvent updated) {
            events.onMemberUpdate(updated);
        } else if (event instanceof GuildMemberRemoveEvent removed) {
            events.onMemberRemove(removed);
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
            String reason = settings.guild()
                    .map(id -> "The bot is not in Discord server " + id + ". Invite it with " + inviteUrl(jda))
                    .orElseGet(() -> "The bot cannot see any channel set under discord: in config.yml. Fix the"
                            + " channel IDs and run /discord reload, or invite the bot with " + inviteUrl(jda));
            LOGGER.warn(reason);
            enter(State.DEGRADED, reason);
            return;
        }
        commands.registerIfChanged(guild, jda.getSelfUser().getApplicationIdLong());
        for (DiscordId channelId : settings.channelIds()) {
            if (guild.getGuildChannelById(channelId.value()) == null) {
                LOGGER.warn(
                        "The bot cannot see Discord channel {} in {}. Give the bot the View Channel permission there,"
                                + " or fix the channel ID in config.yml or with /setup",
                        channelId,
                        guild.getName());
            } else {
                webhooks.prepare(channelId);
            }
        }
        appearance.reapply();
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
        appearance.reapply();
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
            warn("Discord refused the bot's privileged intents, so Discord messages reach Minecraft empty. Turn on"
                    + " Message Content Intent, and Server Members Intent if you use the join gate or role sync, at "
                    + (info == null ? PORTAL : info.botSettingsUrl()) + " and run /discord reload");
            enter(State.CONNECTING, null);
            startSession(false, false);
            return;
        }
        if (code == CloseCode.AUTHENTICATION_FAILED) {
            stop(INVALID_TOKEN);
            return;
        }
        String meaning = code == null ? "close code " + rawCode : code.getMeaning();
        stop("Discord ended the connection (" + meaning + "). Check the bot at "
                + (info == null ? PORTAL : info.botSettingsUrl()) + " and run /discord reload");
    }

    private void stopGracefully(List<OutboundMessage> farewell, Deadline deadline) {
        Session active = session;
        if (active == null) {
            return;
        }
        JDA jda = active.jda();
        jda.cancelRequests();
        for (OutboundMessage message : farewell) {
            var unused = webhooks.sendFinal(message).exceptionally(failure -> {
                LOGGER.warn("Could not send the last message to Discord: {}", Failures.describe(failure));
                return null;
            });
        }
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

    private static long remaining(Deadline deadline) {
        return Math.max(0, deadline.remaining().minus(RELEASE_MARGIN).toNanos());
    }
}
