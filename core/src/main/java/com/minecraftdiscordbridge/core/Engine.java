package com.minecraftdiscordbridge.core;

import static com.minecraftdiscordbridge.core.log.Log.LOGGER;

import com.minecraftdiscordbridge.core.commands.GameCommands;
import com.minecraftdiscordbridge.core.config.BridgeConfig;
import com.minecraftdiscordbridge.core.config.ConfigLoader;
import com.minecraftdiscordbridge.core.config.ConfigProblem;
import com.minecraftdiscordbridge.core.config.ConfigResult;
import com.minecraftdiscordbridge.core.config.ConfigSnapshot;
import com.minecraftdiscordbridge.core.discord.AutocompleteReply;
import com.minecraftdiscordbridge.core.discord.AutocompleteRequest;
import com.minecraftdiscordbridge.core.discord.CommandReply;
import com.minecraftdiscordbridge.core.discord.CommandRequest;
import com.minecraftdiscordbridge.core.discord.Discord;
import com.minecraftdiscordbridge.core.discord.DiscordId;
import com.minecraftdiscordbridge.core.discord.DiscordListener;
import com.minecraftdiscordbridge.core.discord.DiscordMessage;
import com.minecraftdiscordbridge.core.discord.DiscordSettings;
import com.minecraftdiscordbridge.core.discord.DiscordStatus;
import com.minecraftdiscordbridge.core.discord.MemberLookup;
import com.minecraftdiscordbridge.core.discord.OfflineDiscord;
import com.minecraftdiscordbridge.core.discord.OutboundMessage;
import com.minecraftdiscordbridge.core.i18n.Lang;
import com.minecraftdiscordbridge.core.i18n.Language;
import com.minecraftdiscordbridge.core.i18n.Replies;
import com.minecraftdiscordbridge.core.log.Failures;
import com.minecraftdiscordbridge.core.loop.Deadline;
import com.minecraftdiscordbridge.core.platform.CommandSource;
import com.minecraftdiscordbridge.core.platform.GameEvent;
import com.minecraftdiscordbridge.core.platform.JoinDecision;
import com.minecraftdiscordbridge.core.platform.Permission;
import com.minecraftdiscordbridge.core.platform.PlayerProfile;
import com.minecraftdiscordbridge.core.setup.Channels;
import com.minecraftdiscordbridge.core.setup.SetupGuide;
import com.minecraftdiscordbridge.core.status.StatusReport;
import com.minecraftdiscordbridge.core.storage.Link;
import com.minecraftdiscordbridge.core.storage.LinkChange;
import com.minecraftdiscordbridge.core.storage.ShutdownMarker;
import com.minecraftdiscordbridge.core.storage.StoredCommandHashes;
import com.minecraftdiscordbridge.core.storage.StoredWebhooks;
import com.minecraftdiscordbridge.core.storage.Stores;
import com.minecraftdiscordbridge.core.text.Format.Slot;
import com.minecraftdiscordbridge.core.text.TextRenderer;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BooleanSupplier;
import org.jspecify.annotations.Nullable;

final class Engine {
    static final Duration REBIND_DELAY = Duration.ofSeconds(2);

    private enum Outcome {
        APPLIED,
        RECONNECTING,
        NO_TOKEN
    }

    private record Boot(Stores stores, ConfigResult config, boolean recovered) {}

    private record WaitingJoin(PlayerProfile player, boolean bypass, CompletableFuture<JoinDecision> result) {}

    private final Services services;
    private final ExecutorService lifecycle;
    private final DiscordFactory factory;
    private final ConfigLoader loader;
    private final ShutdownMarker marker;
    private final BooleanSupplier stopping;
    private final Object lock = new Object();
    private final List<WaitingJoin> waitingJoins = new ArrayList<>();
    private volatile @Nullable Features features;
    private volatile boolean closed;
    private volatile boolean booting = true;
    private @Nullable Stores stores;
    private @Nullable ConfigSnapshot snapshot;
    private @Nullable DiscordSettings settings;
    private Lang lang = Lang.of(Language.EN);
    private boolean recovered;
    private boolean serverStarted;
    private boolean startAnnounced;
    private int generation;
    private long droppedEvents;

    Engine(
            Services services,
            ExecutorService lifecycle,
            DiscordFactory factory,
            ConfigLoader loader,
            ShutdownMarker marker,
            BooleanSupplier stopping) {
        this.services = services;
        this.lifecycle = lifecycle;
        this.factory = factory;
        this.loader = loader;
        this.marker = marker;
        this.stopping = stopping;
    }

    void boot() {
        services.io()
                .completeOnLoop(
                        () -> new Boot(
                                Stores.open(services.platform().dataDirectory()), loader.load(), marker.markRunning()),
                        this::booted,
                        failure -> {
                            LOGGER.error(
                                    "The bridge could not start: {}. Check that the plugin data folder is readable and"
                                            + " writable, then restart the server",
                                    Failures.describe(failure));
                            releaseWaitingJoins();
                        });
    }

    void onGameEvent(GameEvent event) {
        Features current = features;
        if (event instanceof GameEvent.ServerStarted started) {
            services.roster().reset(started.online());
            serverStarted = true;
            announceStart();
            refreshStatus();
        } else if (event instanceof GameEvent.PlayerJoined joined) {
            services.roster().joined(joined.player(), joined.vanished());
            if (current != null) {
                current.events().joined(joined);
                current.roleSync().playerJoined(joined.player());
            }
            refreshStatus();
        } else if (event instanceof GameEvent.PlayerLeft left) {
            services.roster().left(left.player());
            if (current != null && !stopping.getAsBoolean()) {
                current.events().left(left);
            }
            refreshStatus();
        } else if (event instanceof GameEvent.PlayerSwitchedServer switched) {
            if (switched.from() == null) {
                joinIfNotOnline(switched);
            } else if (current != null) {
                current.events().switched(switched);
            }
        } else if (current == null) {
            return;
        } else if (event instanceof GameEvent.PlayerChatted chatted) {
            current.minecraftChat().onChat(chatted);
        } else if (event instanceof GameEvent.PlayerDied died) {
            current.events().died(died);
        } else if (event instanceof GameEvent.AdvancementEarned earned) {
            current.events().advanced(earned);
        }
    }

    void command(CommandSource source, Set<Permission> granted, List<String> arguments) {
        String subcommand = arguments.isEmpty() ? "" : arguments.get(0).toLowerCase(Locale.ROOT);
        Features current = features;
        Replies replies = current == null ? replies() : current.replies();
        if (subcommand.equals(GameCommands.STATUS)) {
            if (GameCommands.allowed(replies, source, granted, Permission.STATUS)) {
                status(replies, source);
            }
        } else if (subcommand.equals(GameCommands.RELOAD)) {
            if (GameCommands.allowed(replies, source, granted, Permission.RELOAD)) {
                reload(source, replies);
            }
        } else if (current != null) {
            current.commands().handle(source, granted, subcommand);
        } else if (subcommand.isEmpty() || subcommand.equals("help")) {
            GameCommands.help(replies, source, granted);
        } else {
            replies.send(source, "game.not-ready");
        }
    }

    void checkJoin(PlayerProfile player, boolean bypass, CompletableFuture<JoinDecision> result) {
        Features current = features;
        if (current != null) {
            current.gate().decide(player, bypass, result);
        } else if (booting) {
            waitingJoins.add(new WaitingJoin(player, bypass, result));
        } else {
            result.complete(new JoinDecision.Allow());
        }
    }

    JoinDecision joinFallback(PlayerProfile player, boolean bypass) {
        Features current = features;
        if (current != null) {
            return current.gate().fallback(player, bypass);
        }
        if (booting && !bypass) {
            Lang english = Lang.of(Language.EN);
            return new JoinDecision.Deny(TextRenderer.toJson(
                    english.component("game.gate.starting"),
                    services.platform().info().dataVersion()));
        }
        return new JoinDecision.Allow();
    }

    List<OutboundMessage> farewell(boolean crashed) {
        Features current = features;
        if (current == null) {
            return List.of();
        }
        return current.events().serverStopped(crashed).map(List::of).orElse(List.of());
    }

    Discord close() {
        Features current;
        synchronized (lock) {
            closed = true;
            current = features;
        }
        if (current != null) {
            current.close();
        }
        services.vanilla().close();
        return services.discord().swap(OfflineDiscord.stopped(OfflineDiscord.STOPPED));
    }

    void clearMarker() throws IOException {
        marker.clear();
    }

    private void joinIfNotOnline(GameEvent.PlayerSwitchedServer first) {
        if (services.roster().find(first.player().id()).isEmpty()) {
            onGameEvent(new GameEvent.PlayerJoined(first.player(), false));
        }
    }

    private void booted(Boot boot) {
        stores = boot.stores();
        recovered = boot.recovered();
        boot.stores().links().onChange(change -> services.loop().control(() -> linkChanged(change)));
        apply(boot.config(), null);
    }

    private void linkChanged(LinkChange change) {
        if (change instanceof LinkChange.Unlinked) {
            Link link = change.link();
            services.access().forget(link.minecraftId(), link.discordId());
        }
        Features current = features;
        if (current != null) {
            current.roleSync().linkChanged(change);
        }
    }

    private void releaseWaitingJoins() {
        booting = false;
        List<WaitingJoin> waiting = List.copyOf(waitingJoins);
        waitingJoins.clear();
        waiting.forEach(join -> checkJoin(join.player(), join.bypass(), join.result()));
    }

    private void reload(CommandSource source, Replies replies) {
        if (stores == null) {
            replies.send(source, "game.not-ready");
            return;
        }
        services.io().completeOnLoop(loader::load, result -> apply(result, source), failure -> {
            LOGGER.warn(
                    "Could not reload config.yml or secrets.yml: {}. Check that both files are readable and run"
                            + " /discord reload again",
                    Failures.describe(failure));
            replies.send(source, "game.reload.failed");
        });
    }

    private void apply(ConfigResult result, @Nullable CommandSource source) {
        if (closed) {
            releaseWaitingJoins();
            return;
        }
        result.warnings().forEach(warning -> LOGGER.warn("{}", warning));
        if (result instanceof ConfigResult.Failed failed) {
            rejected(failed.errors(), source);
            releaseWaitingJoins();
            return;
        }
        Outcome outcome = activate(((ConfigResult.Loaded) result).snapshot());
        releaseWaitingJoins();
        if (source != null) {
            replies()
                    .send(
                            source,
                            switch (outcome) {
                                case APPLIED -> "game.reload.done";
                                case RECONNECTING -> "game.reload.reconnecting";
                                case NO_TOKEN -> "game.reload.no-token";
                            });
        }
    }

    private void rejected(List<ConfigProblem> errors, @Nullable CommandSource source) {
        errors.forEach(error -> LOGGER.error("{}", error));
        SetupGuide.logConfigErrors();
        if (snapshot == null) {
            services.discord().swap(OfflineDiscord.stopped(OfflineDiscord.CONFIG_ERRORS));
        }
        if (source != null) {
            Replies replies = replies();
            replies.send(source, "game.reload.failed");
            errors.forEach(error -> replies.send(source, "game.reload.error", Slot.text("problem", error.toString())));
            replies.send(source, "game.reload.fix");
        }
    }

    private Outcome activate(ConfigSnapshot loaded) {
        Stores ready = requireStores();
        snapshot = loaded;
        BridgeConfig config = loaded.config();
        lang = Lang.of(config.language());
        Channels channels = Channels.resolve(config.discord(), ready.bindings().current());
        synchronized (lock) {
            if (closed) {
                return Outcome.APPLIED;
            }
            Features previous = features;
            if (previous != null) {
                previous.close();
            }
            features = Features.build(services, ready, loaded, channels, this::rebound);
        }
        Outcome outcome;
        if (!loaded.secrets().hasDiscordToken()) {
            SetupGuide.log(services.platform().dataDirectory());
            settings = null;
            replaceDiscord(OfflineDiscord.stopped(OfflineDiscord.NO_TOKEN));
            outcome = Outcome.NO_TOKEN;
        } else {
            DiscordSettings next = DiscordPlan.settings(loaded, channels);
            if (next.equals(settings) && services.discord().status().state() == DiscordStatus.State.READY) {
                outcome = Outcome.APPLIED;
            } else {
                settings = next;
                connect(next);
                outcome = Outcome.RECONNECTING;
            }
        }
        announceStart();
        refreshStatus();
        Features current = features;
        if (current != null && services.discord().status().isConnected()) {
            current.roleSync().discordReady();
        }
        return outcome;
    }

    private void rebound() {
        services.loop().later(REBIND_DELAY, () -> {
            ConfigSnapshot current = snapshot;
            if (current != null && !closed) {
                activate(current);
            }
        });
    }

    private void connect(DiscordSettings next) {
        int target = ++generation;
        Discord previous = services.discord().swap(OfflineDiscord.connecting(OfflineDiscord.CONNECTING));
        try {
            lifecycle.execute(() -> {
                previous.shutdown(List.of(), Deadline.after(Bridge.DISCORD_STOP_BUDGET));
                services.loop().control(() -> create(target, next));
            });
        } catch (RejectedExecutionException stopped) {
            LOGGER.debug("Not reconnecting to Discord because the bridge is stopping");
        }
    }

    private void replaceDiscord(Discord next) {
        generation++;
        Discord previous = services.discord().swap(next);
        if (previous instanceof OfflineDiscord) {
            return;
        }
        try {
            lifecycle.execute(() -> previous.shutdown(List.of(), Deadline.after(Bridge.DISCORD_STOP_BUDGET)));
        } catch (RejectedExecutionException stopped) {
            LOGGER.debug("Discord is already shutting down");
        }
    }

    private void create(int target, DiscordSettings next) {
        synchronized (lock) {
            if (closed || target != generation) {
                return;
            }
            try {
                Discord created = factory.create(
                        next,
                        services.loop(),
                        new Listener(target),
                        new StoredWebhooks(requireStores().webhooks(), services.io()),
                        new StoredCommandHashes(requireStores().commandHashes(), services.io()));
                services.discord().swap(created);
                created.start();
            } catch (RuntimeException failure) {
                LOGGER.error(
                        "Could not start the Discord connection: {}. Run /discord reload to try again",
                        Failures.describe(failure));
                services.discord().swap(OfflineDiscord.stopped(failure.toString()));
                return;
            }
        }
        refreshStatus();
    }

    private void announceStart() {
        Features current = features;
        if (serverStarted && !startAnnounced && current != null) {
            startAnnounced = true;
            current.events().serverStarted(recovered);
        }
    }

    private void refreshStatus() {
        Features current = features;
        if (current != null) {
            current.status().refresh();
        }
    }

    private void status(Replies replies, CommandSource source) {
        droppedEvents += services.loop().takeDropped();
        Stores ready = stores;
        int links = ready == null ? 0 : ready.links().all().size();
        DiscordStatus status = services.discord().status();
        StatusReport.send(replies, source, status, links, droppedEvents);
    }

    private Replies replies() {
        return new Replies(lang, services.platform().info().dataVersion());
    }

    private Stores requireStores() {
        Stores ready = stores;
        if (ready == null) {
            throw new IllegalStateException("The bridge stores are not open yet");
        }
        return ready;
    }

    private final class Listener implements DiscordListener {
        private final int target;

        Listener(int target) {
            this.target = target;
        }

        @Override
        public void onStatusChanged(DiscordStatus status) {
            if (target == generation && status.state() == DiscordStatus.State.READY) {
                refreshStatus();
            }
            Features current = features;
            if (target == generation && current != null && status.isConnected()) {
                current.roleSync().discordReady();
            }
        }

        @Override
        public void onMessage(DiscordMessage message) {
            Features current = features;
            if (target == generation && current != null) {
                current.discordChat().onMessage(message);
                current.consoleInput().onMessage(message);
            }
        }

        @Override
        public void onMemberUpdated(MemberLookup.Member member) {
            if (target != generation) {
                return;
            }
            services.access().remember(member, services.clock().instant());
            Features current = features;
            if (current != null) {
                current.roleSync().memberUpdated(member);
            }
        }

        @Override
        public void onMemberLeft(DiscordId userId) {
            if (target != generation) {
                return;
            }
            services.access()
                    .remember(
                            new MemberLookup.NotMember(userId), services.clock().instant());
            Features current = features;
            if (current != null) {
                current.roleSync().memberLeft(userId);
            }
        }

        @Override
        public void onCommand(CommandRequest request, CommandReply reply) {
            Features current = features;
            if (target != generation || current == null) {
                reply.replyEphemeral(
                        Lang.forDiscordLocale(request.locale(), lang).text("discord.not-ready"));
                return;
            }
            current.slash().onCommand(request, reply);
        }

        @Override
        public void onAutocomplete(AutocompleteRequest request, AutocompleteReply reply) {
            Features current = features;
            if (target != generation || current == null) {
                reply.respond(List.of());
                return;
            }
            current.slash().onAutocomplete(request, reply);
        }
    }
}
