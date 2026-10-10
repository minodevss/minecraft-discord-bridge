package com.minecraftdiscordbridge.core;

import com.minecraftdiscordbridge.core.commands.GameCommands;
import com.minecraftdiscordbridge.core.config.BridgeConfig;
import com.minecraftdiscordbridge.core.config.Channels;
import com.minecraftdiscordbridge.core.config.ConfigLoader;
import com.minecraftdiscordbridge.core.config.ConfigProblem;
import com.minecraftdiscordbridge.core.config.ConfigResult;
import com.minecraftdiscordbridge.core.config.ConfigSnapshot;
import com.minecraftdiscordbridge.core.config.Language;
import com.minecraftdiscordbridge.core.discord.AutocompleteReply;
import com.minecraftdiscordbridge.core.discord.AutocompleteRequest;
import com.minecraftdiscordbridge.core.discord.Discord;
import com.minecraftdiscordbridge.core.discord.DiscordListener;
import com.minecraftdiscordbridge.core.discord.DiscordMessage;
import com.minecraftdiscordbridge.core.discord.DiscordSettings;
import com.minecraftdiscordbridge.core.discord.DiscordStatus;
import com.minecraftdiscordbridge.core.discord.InteractionReply;
import com.minecraftdiscordbridge.core.discord.InteractionRequest;
import com.minecraftdiscordbridge.core.discord.OutboundMessage;
import com.minecraftdiscordbridge.core.i18n.Lang;
import com.minecraftdiscordbridge.core.i18n.Replies;
import com.minecraftdiscordbridge.core.platform.CommandSource;
import com.minecraftdiscordbridge.core.platform.GameEvent;
import com.minecraftdiscordbridge.core.platform.Permission;
import com.minecraftdiscordbridge.core.text.Format.Slot;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BooleanSupplier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class Engine {
    static final Duration REBIND_DELAY = Duration.ofSeconds(2);
    private static final Logger LOGGER = LoggerFactory.getLogger("MinecraftDiscordBridge");

    private enum Outcome {
        APPLIED,
        RECONNECTING,
        NO_TOKEN
    }

    private record Boot(Stores stores, ConfigResult config, boolean recovered) {}

    private final Shared shared;
    private final ExecutorService lifecycle;
    private final DiscordFactory factory;
    private final ConfigLoader loader;
    private final ShutdownMarker marker;
    private final BooleanSupplier stopping;
    private final Object lock = new Object();
    private volatile @Nullable Session session;
    private volatile boolean closed;
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
            Shared shared,
            ExecutorService lifecycle,
            DiscordFactory factory,
            ConfigLoader loader,
            ShutdownMarker marker,
            BooleanSupplier stopping) {
        this.shared = shared;
        this.lifecycle = lifecycle;
        this.factory = factory;
        this.loader = loader;
        this.marker = marker;
        this.stopping = stopping;
    }

    void boot() {
        shared.io()
                .call(
                        () -> new Boot(
                                Stores.open(shared.platform().dataDirectory()), loader.load(), marker.markRunning()),
                        this::booted,
                        failure -> {
                            LOGGER.error(
                                    "The bridge could not start: {}. Check that the plugin data folder is readable and"
                                            + " writable, then restart the server",
                                    failure.toString());
                        });
    }

    void onGameEvent(GameEvent event) {
        Session current = session;
        if (event instanceof GameEvent.ServerStarted started) {
            shared.roster().reset(started.online());
            serverStarted = true;
            announceStart();
            refreshStatus();
        } else if (event instanceof GameEvent.PlayerJoined joined) {
            shared.roster().joined(joined.player(), joined.vanished());
            if (current != null) {
                current.events().joined(joined);
            }
            refreshStatus();
        } else if (event instanceof GameEvent.PlayerLeft left) {
            shared.roster().left(left.player());
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
        Session current = session;
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

    List<OutboundMessage> farewell(boolean crashed) {
        Session current = session;
        if (current == null) {
            return List.of();
        }
        return current.events().serverStopped(crashed).map(List::of).orElse(List.of());
    }

    Discord close() {
        Session current;
        synchronized (lock) {
            closed = true;
            current = session;
        }
        if (current != null) {
            current.close();
        }
        shared.vanilla().close();
        return shared.discord().swap(OfflineDiscord.stopped(OfflineDiscord.STOPPED));
    }

    void clearMarker() throws IOException {
        marker.clear();
    }

    private void joinIfNotOnline(GameEvent.PlayerSwitchedServer first) {
        if (shared.roster().find(first.player().id()).isEmpty()) {
            onGameEvent(new GameEvent.PlayerJoined(first.player(), false));
        }
    }

    private void booted(Boot boot) {
        stores = boot.stores();
        recovered = boot.recovered();
        apply(boot.config(), null);
    }

    private void reload(CommandSource source, Replies replies) {
        if (stores == null) {
            replies.send(source, "game.not-ready");
            return;
        }
        shared.io().call(loader::load, result -> apply(result, source), failure -> {
            LOGGER.warn(
                    "Could not reload config.yml or secrets.yml: {}. Check that both files are readable and run"
                            + " /discord reload again",
                    failure.toString());
            replies.send(source, "game.reload.failed");
        });
    }

    private void apply(ConfigResult result, @Nullable CommandSource source) {
        if (closed) {
            return;
        }
        result.warnings().forEach(warning -> LOGGER.warn("{}", warning));
        if (result instanceof ConfigResult.Failed failed) {
            rejected(failed.errors(), source);
            return;
        }
        Outcome outcome = activate(((ConfigResult.Loaded) result).snapshot());
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
            shared.discord().swap(OfflineDiscord.stopped(OfflineDiscord.CONFIG_ERRORS));
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
            Session previous = session;
            if (previous != null) {
                previous.close();
            }
            session = Sessions.build(shared, ready, loaded, channels, this::rebound);
        }
        Outcome outcome;
        if (!loaded.secrets().hasDiscordToken()) {
            SetupGuide.log(shared.platform().dataDirectory());
            settings = null;
            replaceDiscord(OfflineDiscord.stopped(OfflineDiscord.NO_TOKEN));
            outcome = Outcome.NO_TOKEN;
        } else {
            DiscordSettings next = DiscordPlan.settings(loaded, channels);
            if (next.equals(settings) && shared.discord().status().state() == DiscordStatus.State.READY) {
                outcome = Outcome.APPLIED;
            } else {
                settings = next;
                connect(next);
                outcome = Outcome.RECONNECTING;
            }
        }
        announceStart();
        refreshStatus();
        return outcome;
    }

    private void rebound() {
        var unused = shared.loop().schedule(REBIND_DELAY, () -> {
            ConfigSnapshot current = snapshot;
            if (current != null && !closed) {
                activate(current);
            }
        });
    }

    private void connect(DiscordSettings next) {
        int target = ++generation;
        Discord previous = shared.discord().swap(OfflineDiscord.connecting(OfflineDiscord.CONNECTING));
        try {
            lifecycle.execute(() -> {
                previous.shutdown(List.of());
                shared.loop().control(() -> create(target, next));
            });
        } catch (RejectedExecutionException stopped) {
            LOGGER.debug("Not reconnecting to Discord because the bridge is stopping");
        }
    }

    private void replaceDiscord(Discord next) {
        generation++;
        Discord previous = shared.discord().swap(next);
        if (previous instanceof OfflineDiscord) {
            return;
        }
        try {
            lifecycle.execute(() -> previous.shutdown(List.of()));
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
                        shared.loop(),
                        new Listener(target),
                        new StoredWebhooks(requireStores().webhooks(), shared.io()),
                        new StoredCommandHashes(requireStores().commandHashes(), shared.io()));
                shared.discord().swap(created);
                created.start();
            } catch (RuntimeException failure) {
                LOGGER.error(
                        "Could not start the Discord connection: {}. Run /discord reload to try again",
                        failure.toString());
                shared.discord().swap(OfflineDiscord.stopped(failure.toString()));
                return;
            }
        }
        refreshStatus();
    }

    private void announceStart() {
        Session current = session;
        if (serverStarted && !startAnnounced && current != null) {
            startAnnounced = true;
            current.events().serverStarted(recovered);
        }
    }

    private void refreshStatus() {
        Session current = session;
        if (current != null) {
            current.status().refresh();
        }
    }

    private void status(Replies replies, CommandSource source) {
        droppedEvents += shared.loop().takeDropped();
        Stores ready = stores;
        int links = ready == null ? 0 : ready.links().all().size();
        DiscordStatus status = shared.discord().status();
        StatusReport.send(replies, source, status, links, droppedEvents);
    }

    private Replies replies() {
        return new Replies(lang, shared.platform().info().dataVersion());
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
        }

        @Override
        public void onMessage(DiscordMessage message) {
            Session current = session;
            if (target == generation && current != null) {
                current.discordChat().onMessage(message);
            }
        }

        @Override
        public void onCommand(InteractionRequest request, InteractionReply reply) {
            Session current = session;
            if (target != generation || current == null) {
                reply.replyEphemeral(
                        Lang.forDiscordLocale(request.locale(), lang).text("discord.not-ready"));
                return;
            }
            current.slash().onCommand(request, reply);
        }

        @Override
        public void onAutocomplete(AutocompleteRequest request, AutocompleteReply reply) {
            Session current = session;
            if (target != generation || current == null) {
                reply.respond(List.of());
                return;
            }
            current.slash().onAutocomplete(request, reply);
        }
    }
}
