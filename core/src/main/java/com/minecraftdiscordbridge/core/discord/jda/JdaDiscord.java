package com.minecraftdiscordbridge.core.discord.jda;

import static com.minecraftdiscordbridge.core.log.Log.LOGGER;

import com.minecraftdiscordbridge.core.discord.CommandHashRepository;
import com.minecraftdiscordbridge.core.discord.Discord;
import com.minecraftdiscordbridge.core.discord.DiscordId;
import com.minecraftdiscordbridge.core.discord.DiscordListener;
import com.minecraftdiscordbridge.core.discord.DiscordSettings;
import com.minecraftdiscordbridge.core.discord.DiscordStatus;
import com.minecraftdiscordbridge.core.discord.DiscordStatus.ChannelStatus;
import com.minecraftdiscordbridge.core.discord.MemberLookup;
import com.minecraftdiscordbridge.core.discord.OutboundMessage;
import com.minecraftdiscordbridge.core.discord.RoleInfo;
import com.minecraftdiscordbridge.core.discord.WebhookRepository;
import com.minecraftdiscordbridge.core.log.Failures;
import com.minecraftdiscordbridge.core.loop.CoreLoop;
import com.minecraftdiscordbridge.core.loop.Deadline;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberUpdateEvent;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.events.session.ShutdownEvent;
import net.dv8tion.jda.api.requests.RestConfig;
import org.jspecify.annotations.Nullable;

public final class JdaDiscord implements Discord {
    private static final Permission[] INVITE_PERMISSIONS = {
        Permission.VIEW_CHANNEL,
        Permission.MESSAGE_SEND,
        Permission.MESSAGE_EMBED_LINKS,
        Permission.MESSAGE_HISTORY,
        Permission.MANAGE_WEBHOOKS,
        Permission.MANAGE_CHANNEL
    };

    record Endpoints(String restBaseUrl, Optional<String> gatewayUrl) {
        static final Endpoints DISCORD = new Endpoints(RestConfig.DEFAULT_BASE_URL, Optional.empty());
    }

    private final DiscordSettings settings;
    private final GuildScope scope;
    private final CoreLoop loop;
    private final DiscordListener listener;
    private final RateLimitCounter rateLimits = new RateLimitCounter();
    private final JdaConnection connection;
    private final JdaGuild guild;
    private final WebhookSender webhooks;
    private final Outbox outbox;
    private final CommandRegistrar commands;
    private final JdaEvents events;
    private final Appearance appearance;
    private final AtomicBoolean stopped = new AtomicBoolean();

    JdaDiscord(
            DiscordSettings settings,
            Endpoints endpoints,
            CoreLoop loop,
            DiscordListener listener,
            WebhookRepository webhookRepository,
            CommandHashRepository commandHashes) {
        this.settings = settings;
        this.scope = new GuildScope(settings);
        this.loop = loop;
        this.listener = listener;
        this.connection = new JdaConnection(settings, endpoints, rateLimits, new ConnectionCallbacks());
        this.guild = new JdaGuild(this::currentGuild);
        this.webhooks = new WebhookSender(connection::jda, webhookRepository, rateLimits, System::nanoTime);
        this.outbox = new Outbox(settings.serverAuthor(), webhooks, loop::control);
        this.commands = new CommandRegistrar(settings.commands(), commandHashes);
        this.events = new JdaEvents(settings, scope, loop, listener, webhooks);
        this.appearance = new Appearance(connection::jda, System::nanoTime, loop);
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
        connection.start();
    }

    @Override
    public DiscordStatus status() {
        Map<DiscordId, ChannelStatus> channels = new HashMap<>();
        settings.channelIds().forEach(id -> channels.put(id, ChannelStatus.IDLE));
        channels.putAll(outbox.snapshot());
        channels.replaceAll((id, channel) -> channel.withRateLimited(rateLimits.count(id)));
        return connection.status(channels);
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
        return connection
                .application()
                .map(info -> info.isOwner(userId.value()))
                .orElse(false);
    }

    @Override
    public CompletableFuture<MemberLookup> member(DiscordId userId) {
        return guild.member(userId);
    }

    @Override
    public Map<DiscordId, RoleInfo> roles() {
        return guild.roles();
    }

    @Override
    public CompletableFuture<Void> addRole(DiscordId userId, DiscordId roleId) {
        return guild.changeRole(userId, roleId, true);
    }

    @Override
    public CompletableFuture<Void> removeRole(DiscordId userId, DiscordId roleId) {
        return guild.changeRole(userId, roleId, false);
    }

    @Override
    public void shutdown(List<OutboundMessage> finalMessages, Deadline deadline) {
        if (!stopped.compareAndSet(false, true)) {
            return;
        }
        List<OutboundMessage> farewell = List.copyOf(finalMessages);
        outbox.close();
        connection.shutdown(deadline, jda -> sendFarewell(farewell));
    }

    @Nullable
    JDA currentJda() {
        return connection.jda();
    }

    private @Nullable Guild currentGuild() {
        JDA jda = connection.jda();
        return jda == null || connection.closing() ? null : scope.current(jda);
    }

    private void sendFarewell(List<OutboundMessage> farewell) {
        for (OutboundMessage message : farewell) {
            var unused = webhooks.sendFinal(message).exceptionally(failure -> {
                LOGGER.warn("Could not send the last message to Discord: {}", Failures.describe(failure));
                return null;
            });
        }
    }

    private void onEvent(int generation, GenericEvent event) {
        if (connection.closing()) {
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
            connection.sessionEnded(generation, ended.getCloseCode(), ended.getCode());
        }
    }

    private void onReady(int generation, JDA jda) {
        if (connection.closing() || !connection.isCurrent(generation)) {
            return;
        }
        if (scope.waitingForSetup()) {
            readyForSetup(jda);
            return;
        }
        Guild guild = scope.resolve(jda);
        if (guild == null) {
            connection.degrade(settings.guild()
                    .map(id -> "The bot is not in Discord server " + id + ". Invite it with " + inviteUrl(jda))
                    .orElseGet(() -> "The bot cannot see any channel set under discord: in config.yml. Fix the"
                            + " channel IDs and run /discord reload, or invite the bot with " + inviteUrl(jda)));
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
        goLive("Connected to Discord as " + jda.getSelfUser().getName() + " in " + guild.getName());
    }

    private void readyForSetup(JDA jda) {
        List<Guild> guilds = jda.getGuilds();
        if (guilds.isEmpty()) {
            connection.degrade("The bot is not in any Discord server. Invite it with " + inviteUrl(jda));
            return;
        }
        long applicationId = jda.getSelfUser().getApplicationIdLong();
        guilds.forEach(guild -> commands.registerIfChanged(guild, applicationId));
        goLive("Connected to Discord as " + jda.getSelfUser().getName()
                + ". Run /setup in the Discord channel that should show the chat");
    }

    private void goLive(String connected) {
        appearance.reapply();
        outbox.open();
        LOGGER.info(connected);
        connection.ready();
    }

    private static String inviteUrl(JDA jda) {
        return "https://discord.com/oauth2/authorize?client_id="
                + jda.getSelfUser().getApplicationId() + "&scope=bot+applications.commands&permissions="
                + Permission.getRaw(INVITE_PERMISSIONS);
    }

    private void onLoop(Runnable task) {
        if (loop.inLoop()) {
            task.run();
        } else {
            loop.control(task);
        }
    }

    private final class ConnectionCallbacks implements JdaConnection.Callbacks {
        @Override
        public void onEvent(int generation, GenericEvent event) {
            JdaDiscord.this.onEvent(generation, event);
        }

        @Override
        public void onStatusChanged() {
            loop.control(() -> listener.onStatusChanged(status()));
        }

        @Override
        public void onSessionLost() {
            loop.control(outbox::hold);
        }
    }
}
