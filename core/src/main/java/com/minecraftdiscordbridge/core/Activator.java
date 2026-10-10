package com.minecraftdiscordbridge.core;

import static com.minecraftdiscordbridge.core.log.Log.LOGGER;

import com.minecraftdiscordbridge.core.commands.BridgeCommand;
import com.minecraftdiscordbridge.core.config.BridgeConfig;
import com.minecraftdiscordbridge.core.config.ConfigLoader;
import com.minecraftdiscordbridge.core.config.ConfigProblem;
import com.minecraftdiscordbridge.core.config.ConfigResult;
import com.minecraftdiscordbridge.core.config.ConfigSnapshot;
import com.minecraftdiscordbridge.core.discord.Discord;
import com.minecraftdiscordbridge.core.discord.DiscordStatus;
import com.minecraftdiscordbridge.core.discord.OutboundMessage;
import com.minecraftdiscordbridge.core.i18n.Lang;
import com.minecraftdiscordbridge.core.i18n.Language;
import com.minecraftdiscordbridge.core.i18n.Replies;
import com.minecraftdiscordbridge.core.log.Failures;
import com.minecraftdiscordbridge.core.platform.CommandSource;
import com.minecraftdiscordbridge.core.platform.GameCommand;
import com.minecraftdiscordbridge.core.platform.GameEvent;
import com.minecraftdiscordbridge.core.platform.PlatformInfo;
import com.minecraftdiscordbridge.core.setup.Channels;
import com.minecraftdiscordbridge.core.setup.SetupGuide;
import com.minecraftdiscordbridge.core.status.StatusReport;
import com.minecraftdiscordbridge.core.storage.Link;
import com.minecraftdiscordbridge.core.storage.LinkChange;
import com.minecraftdiscordbridge.core.storage.ShutdownMarker;
import com.minecraftdiscordbridge.core.storage.Stores;
import com.minecraftdiscordbridge.core.text.Format.Slot;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.BooleanSupplier;
import org.jspecify.annotations.Nullable;

final class Activator implements BridgeCommand.Callbacks {
    static final Duration REBIND_DELAY = Duration.ofSeconds(2);

    private record Boot(Stores stores, ConfigResult config, boolean recovered) {}

    private final Services services;
    private final DiscordConnection connection;
    private final JoinQueue joins;
    private final ConfigLoader loader;
    private final ShutdownMarker marker;
    private final BooleanSupplier stopping;
    private final Object lock = new Object();
    private volatile @Nullable Features features;
    private volatile boolean closed;
    private @Nullable Stores stores;
    private @Nullable ConfigSnapshot snapshot;
    private Lang lang = Lang.of(Language.EN);
    private volatile boolean linkingEnabled = BridgeConfig.defaults().linking().enabled();
    private boolean recovered;
    private boolean serverStarted;
    private boolean startAnnounced;
    private long droppedEvents;

    Activator(
            Services services,
            ExecutorService lifecycle,
            DiscordConnection.Factory factory,
            ConfigLoader loader,
            ShutdownMarker marker,
            BooleanSupplier stopping) {
        this.services = services;
        this.connection =
                new DiscordConnection(services, lifecycle, factory, () -> Optional.ofNullable(features), () -> lang);
        this.joins = new JoinQueue(
                () -> Optional.ofNullable(features).map(Features::gate),
                services.platform().info().dataVersion());
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
                            joins.release();
                        });
    }

    void onGameEvent(GameEvent event) {
        if (event instanceof GameEvent.ServerStarted started) {
            services.roster().reset(started.online());
            serverStarted = true;
            announceStart();
        } else if (event instanceof GameEvent.PlayerJoined joined) {
            services.roster().joined(joined.player(), joined.vanished());
        } else if (event instanceof GameEvent.PlayerLeft left) {
            services.roster().left(left.player());
        } else if (event instanceof GameEvent.PlayerSwitchedServer switched && switched.from() == null) {
            joinIfNotOnline(switched);
            return;
        }
        Features current = features;
        if (current != null) {
            current.onGameEvent(event, stopping.getAsBoolean());
        }
    }

    void command(CommandSource source, GameCommand command, boolean permitted) {
        bridgeCommand().run(source, command, permitted);
    }

    void help(CommandSource source, Set<GameCommand> permitted) {
        bridgeCommand().help(source, permitted, linkingEnabled);
    }

    boolean linkingEnabled() {
        return linkingEnabled;
    }

    private BridgeCommand bridgeCommand() {
        Features current = features;
        return current == null ? BridgeCommand.idle(replies(), this) : current.command();
    }

    JoinQueue joins() {
        return joins;
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
        connection.stopConnecting();
        if (current != null) {
            current.close();
        }
        services.vanilla().close();
        return connection.disconnect();
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

    @Override
    public void reload(CommandSource source, Replies replies) {
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
            joins.release();
            return;
        }
        result.warnings().forEach(warning -> LOGGER.warn("{}", warning));
        if (result instanceof ConfigResult.Failed failed) {
            rejected(failed.errors(), source);
            joins.release();
            return;
        }
        ConfigSnapshot loaded = ((ConfigResult.Loaded) result).snapshot();
        warnAboutUnsupported(loaded.config());
        DiscordConnection.Outcome outcome = activate(loaded);
        joins.release();
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
            connection.showConfigErrors();
        }
        if (source != null) {
            Replies replies = replies();
            replies.send(source, "game.reload.failed");
            errors.forEach(error -> replies.send(source, "game.reload.error", Slot.text("problem", error.toString())));
            replies.send(source, "game.reload.fix");
        }
    }

    private DiscordConnection.Outcome activate(ConfigSnapshot loaded) {
        Stores ready = requireStores();
        snapshot = loaded;
        BridgeConfig config = loaded.config();
        lang = Lang.of(config.language());
        linkingEnabled = config.linking().enabled();
        Channels channels = Channels.resolve(config, ready.bindings().current());
        synchronized (lock) {
            if (closed) {
                return DiscordConnection.Outcome.APPLIED;
            }
            Features previous = features;
            if (previous != null) {
                previous.close();
            }
            features = Features.build(services, ready, loaded, channels, this::rebound, this);
        }
        DiscordConnection.Outcome outcome = connection.apply(loaded, channels, ready);
        announceStart();
        refreshStatus();
        Features current = features;
        if (current != null && services.discord().status().isConnected()) {
            current.roleSync().discordReady();
        }
        return outcome;
    }

    private void warnAboutUnsupported(BridgeConfig config) {
        PlatformInfo platform = services.platform().info();
        if (config.discordCommands().whitelist() && !platform.supportsWhitelist()) {
            LOGGER.warn(
                    "discord-commands.whitelist has no effect on {}, which has no whitelist; set it to false in config.yml",
                    platform.software());
        }
    }

    private void rebound() {
        services.loop().later(REBIND_DELAY, () -> {
            ConfigSnapshot current = snapshot;
            if (current != null && !closed) {
                activate(current);
            }
        });
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

    @Override
    public void status(CommandSource source, Replies replies) {
        droppedEvents += services.loop().takeDropped();
        Stores ready = stores;
        int links = ready == null ? 0 : ready.links().all().size();
        DiscordStatus status = services.discord().status();
        ConfigSnapshot current = snapshot;
        Optional<String> tokenSource = current == null || !current.secrets().hasDiscordToken()
                ? Optional.empty()
                : Optional.of(current.secrets().discordTokenSource());
        StatusReport.send(replies, source, status, tokenSource, links, droppedEvents);
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
}
