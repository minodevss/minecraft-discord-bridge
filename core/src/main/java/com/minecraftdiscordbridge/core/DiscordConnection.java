package com.minecraftdiscordbridge.core;

import static com.minecraftdiscordbridge.core.log.Log.LOGGER;

import com.minecraftdiscordbridge.core.commands.DiscordCommands;
import com.minecraftdiscordbridge.core.config.BridgeConfig;
import com.minecraftdiscordbridge.core.config.ConfigSnapshot;
import com.minecraftdiscordbridge.core.discord.Author;
import com.minecraftdiscordbridge.core.discord.AutocompleteReply;
import com.minecraftdiscordbridge.core.discord.AutocompleteRequest;
import com.minecraftdiscordbridge.core.discord.CommandHashRepository;
import com.minecraftdiscordbridge.core.discord.CommandReply;
import com.minecraftdiscordbridge.core.discord.CommandRequest;
import com.minecraftdiscordbridge.core.discord.CommandSpec;
import com.minecraftdiscordbridge.core.discord.Discord;
import com.minecraftdiscordbridge.core.discord.DiscordId;
import com.minecraftdiscordbridge.core.discord.DiscordListener;
import com.minecraftdiscordbridge.core.discord.DiscordMessage;
import com.minecraftdiscordbridge.core.discord.DiscordSettings;
import com.minecraftdiscordbridge.core.discord.DiscordStatus;
import com.minecraftdiscordbridge.core.discord.MemberLookup;
import com.minecraftdiscordbridge.core.discord.OfflineDiscord;
import com.minecraftdiscordbridge.core.discord.StatusReason;
import com.minecraftdiscordbridge.core.discord.WebhookRepository;
import com.minecraftdiscordbridge.core.i18n.Lang;
import com.minecraftdiscordbridge.core.log.Failures;
import com.minecraftdiscordbridge.core.loop.CoreLoop;
import com.minecraftdiscordbridge.core.loop.Deadline;
import com.minecraftdiscordbridge.core.platform.PlatformInfo;
import com.minecraftdiscordbridge.core.setup.Channels;
import com.minecraftdiscordbridge.core.setup.SetupGuide;
import com.minecraftdiscordbridge.core.storage.StoredCommandHashes;
import com.minecraftdiscordbridge.core.storage.StoredWebhooks;
import com.minecraftdiscordbridge.core.storage.Stores;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

final class DiscordConnection {
    enum Outcome {
        APPLIED,
        RECONNECTING,
        NO_TOKEN
    }

    @FunctionalInterface
    interface Factory {
        Discord create(
                DiscordSettings settings,
                CoreLoop loop,
                DiscordListener listener,
                WebhookRepository webhooks,
                CommandHashRepository commandHashes);
    }

    private final Services services;
    private final ExecutorService lifecycle;
    private final Factory factory;
    private final Supplier<Optional<Features>> features;
    private final Supplier<Lang> lang;
    private final Object lock = new Object();
    private boolean closed;
    private int generation;
    private @Nullable DiscordSettings settings;

    DiscordConnection(
            Services services,
            ExecutorService lifecycle,
            Factory factory,
            Supplier<Optional<Features>> features,
            Supplier<Lang> lang) {
        this.services = services;
        this.lifecycle = lifecycle;
        this.factory = factory;
        this.features = features;
        this.lang = lang;
    }

    static Set<DiscordId> channelIds(BridgeConfig config, Channels channels) {
        Set<DiscordId> ids = new LinkedHashSet<>();
        channels.chat().ifPresent(ids::add);
        channels.events().ifPresent(ids::add);
        config.console().channel().ifPresent(ids::add);
        return ids;
    }

    Outcome apply(ConfigSnapshot loaded, Channels channels, Stores stores) {
        if (!loaded.secrets().hasDiscordToken()) {
            SetupGuide.log(services.platform().dataDirectory());
            settings = null;
            replace(OfflineDiscord.stopped(OfflineDiscord.NO_TOKEN));
            return Outcome.NO_TOKEN;
        }
        DiscordSettings next = settings(loaded, channels, services.platform().info());
        if (next.equals(settings) && services.discord().status().state() == DiscordStatus.State.READY) {
            return Outcome.APPLIED;
        }
        settings = next;
        connect(next, stores);
        return Outcome.RECONNECTING;
    }

    void showConfigErrors() {
        services.discord().swap(OfflineDiscord.stopped(OfflineDiscord.CONFIG_ERRORS));
    }

    void stopConnecting() {
        synchronized (lock) {
            closed = true;
        }
    }

    Discord disconnect() {
        return services.discord().swap(OfflineDiscord.stopped(OfflineDiscord.STOPPED));
    }

    private static DiscordSettings settings(ConfigSnapshot snapshot, Channels channels, PlatformInfo platform) {
        BridgeConfig config = snapshot.config();
        Set<DiscordId> channelIds = channelIds(config, channels);
        List<CommandSpec> commands =
                channelIds.isEmpty() ? DiscordCommands.setupOnly() : DiscordCommands.forConfig(config, platform);
        return new DiscordSettings(
                snapshot.secrets().discordToken(),
                guild(config, channels),
                channelIds,
                Author.server(),
                needsMembers(config),
                commands);
    }

    private static boolean needsMembers(BridgeConfig config) {
        return !config.roleSync().isEmpty() || config.linking().requiredRole().isPresent();
    }

    private static Optional<DiscordId> guild(BridgeConfig config, Channels channels) {
        if (config.chat().channel().isPresent() || config.events().channel().isPresent()) {
            return Optional.empty();
        }
        return channels.guild();
    }

    private void connect(DiscordSettings next, Stores stores) {
        int target = ++generation;
        Discord previous = services.discord().swap(OfflineDiscord.connecting(OfflineDiscord.CONNECTING));
        try {
            lifecycle.execute(() -> {
                previous.shutdown(List.of(), Deadline.after(Bridge.DISCORD_STOP_BUDGET));
                services.loop().control(() -> create(target, next, stores));
            });
        } catch (RejectedExecutionException stopped) {
            LOGGER.debug("Not reconnecting to Discord because the bridge is stopping");
        }
    }

    private void replace(Discord next) {
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

    private void create(int target, DiscordSettings next, Stores stores) {
        synchronized (lock) {
            if (closed || target != generation) {
                return;
            }
            try {
                Discord created = factory.create(
                        next,
                        services.loop(),
                        new Listener(target),
                        new StoredWebhooks(stores.webhooks(), services.io()),
                        new StoredCommandHashes(stores.commandHashes(), services.io()));
                services.discord().swap(created);
                created.start();
            } catch (RuntimeException failure) {
                LOGGER.error(
                        "Could not start the Discord connection: {}. Run /discord reload to try again",
                        Failures.describe(failure));
                services.discord()
                        .swap(OfflineDiscord.stopped(
                                new StatusReason(StatusReason.Kind.START_FAILED, Failures.describe(failure))));
                return;
            }
        }
        features.get().ifPresent(current -> current.status().refresh());
    }

    private final class Listener implements DiscordListener {
        private final int target;

        Listener(int target) {
            this.target = target;
        }

        @Override
        public void onStatusChanged(DiscordStatus status) {
            Optional<Features> current = current();
            if (status.state() == DiscordStatus.State.READY) {
                current.ifPresent(ready -> ready.status().refresh());
            }
            if (status.isConnected()) {
                current.ifPresent(ready -> ready.roleSync().discordReady());
            }
        }

        @Override
        public void onMessage(DiscordMessage message) {
            current().ifPresent(current -> {
                current.discordChat().onMessage(message);
                current.consoleInput().onMessage(message);
            });
        }

        @Override
        public void onMemberUpdated(MemberLookup.Member member) {
            if (target != generation) {
                return;
            }
            services.access().remember(member, services.clock().instant());
            features.get().ifPresent(current -> current.roleSync().memberUpdated(member));
        }

        @Override
        public void onMemberLeft(DiscordId userId) {
            if (target != generation) {
                return;
            }
            services.access()
                    .remember(
                            new MemberLookup.NotMember(userId), services.clock().instant());
            features.get().ifPresent(current -> current.roleSync().memberLeft(userId));
        }

        @Override
        public void onCommand(CommandRequest request, CommandReply reply) {
            Optional<Features> current = current();
            if (current.isEmpty()) {
                reply.replyEphemeral(
                        Lang.forDiscordLocale(request.locale(), lang.get()).text("discord.not-ready"));
                return;
            }
            current.get().discordCommands().onCommand(request, reply);
        }

        @Override
        public void onAutocomplete(AutocompleteRequest request, AutocompleteReply reply) {
            Optional<Features> current = current();
            if (current.isEmpty()) {
                reply.respond(List.of());
                return;
            }
            current.get().discordCommands().onAutocomplete(request, reply);
        }

        private Optional<Features> current() {
            return target == generation ? features.get() : Optional.empty();
        }
    }
}
