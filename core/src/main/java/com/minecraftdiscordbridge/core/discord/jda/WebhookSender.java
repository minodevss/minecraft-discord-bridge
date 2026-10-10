package com.minecraftdiscordbridge.core.discord.jda;

import static com.minecraftdiscordbridge.core.log.Log.LOGGER;

import com.minecraftdiscordbridge.core.discord.Author;
import com.minecraftdiscordbridge.core.discord.OutboundMessage;
import com.minecraftdiscordbridge.core.discord.WebhookCredentials;
import com.minecraftdiscordbridge.core.discord.WebhookRepository;
import com.minecraftdiscordbridge.core.log.Failures;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.Webhook;
import net.dv8tion.jda.api.entities.WebhookClient;
import net.dv8tion.jda.api.entities.WebhookType;
import net.dv8tion.jda.api.entities.channel.attribute.IWebhookContainer;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import org.jspecify.annotations.Nullable;

final class WebhookSender implements MessageSender {
    static final String WEBHOOK_NAME = "Minecraft Bridge";
    static final int CHANNEL_WEBHOOK_LIMIT = 15;
    private static final Duration FALLBACK_RETRY = Duration.ofMinutes(10);

    private final Supplier<@Nullable JDA> jda;
    private final WebhookRepository repository;
    private final RateLimitCounter rateLimits;
    private final LongSupplier nanoClock;
    private final Map<Long, WebhookCredentials> known = new ConcurrentHashMap<>();
    private final Map<Long, Long> botOnlyUntil = new ConcurrentHashMap<>();
    private final Map<Long, CompletableFuture<Optional<WebhookCredentials>>> resolving = new ConcurrentHashMap<>();

    WebhookSender(
            Supplier<@Nullable JDA> jda,
            WebhookRepository repository,
            RateLimitCounter rateLimits,
            LongSupplier nanoClock) {
        this.jda = jda;
        this.repository = repository;
        this.rateLimits = rateLimits;
        this.nanoClock = nanoClock;
    }

    @Override
    public CompletionStage<Void> send(OutboundMessage message) {
        long channelId = message.channelId();
        JDA api = jda.get();
        if (api == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Discord is not connected yet"));
        }
        return webhookFor(api, channelId)
                .thenCompose(webhook ->
                        webhook.isPresent() ? execute(api, webhook.get(), message, true) : sendAsBot(api, message));
    }

    CompletableFuture<Void> sendFinal(OutboundMessage message) {
        JDA api = jda.get();
        if (api == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Discord is not connected"));
        }
        WebhookCredentials webhook = known.get(message.channelId());
        return webhook == null ? sendAsBot(api, message) : execute(api, webhook, message, false);
    }

    void prepare(long channelId) {
        JDA api = jda.get();
        if (api != null) {
            var unused = webhookFor(api, channelId);
        }
    }

    boolean isOwnWebhook(long webhookId) {
        return known.values().stream().anyMatch(webhook -> webhook.id() == webhookId);
    }

    private CompletionStage<Optional<WebhookCredentials>> webhookFor(JDA api, long channelId) {
        WebhookCredentials cached = known.get(channelId);
        if (cached != null) {
            return CompletableFuture.completedFuture(Optional.of(cached));
        }
        Long until = botOnlyUntil.get(channelId);
        if (until != null && nanoClock.getAsLong() - until < 0) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        CompletableFuture<Optional<WebhookCredentials>> started = new CompletableFuture<>();
        CompletableFuture<Optional<WebhookCredentials>> running = resolving.putIfAbsent(channelId, started);
        if (running != null) {
            return running;
        }
        var unused = resolve(api, channelId).whenComplete((webhook, failure) -> {
            resolving.remove(channelId, started);
            if (failure == null) {
                started.complete(webhook);
            } else {
                started.completeExceptionally(failure);
            }
        });
        return started;
    }

    private CompletionStage<Optional<WebhookCredentials>> resolve(JDA api, long channelId) {
        return repository
                .find(channelId)
                .exceptionally(failure -> Optional.empty())
                .thenCompose(stored -> stored.isPresent()
                        ? CompletableFuture.completedFuture(Optional.of(remember(channelId, stored.get())))
                        : discover(api, channelId));
    }

    private CompletionStage<Optional<WebhookCredentials>> discover(JDA api, long channelId) {
        IWebhookContainer channel = api.getChannelById(IWebhookContainer.class, channelId);
        if (channel == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        if (!channel.getGuild().getSelfMember().hasPermission(channel, Permission.MANAGE_WEBHOOKS)) {
            return botOnly(
                    channelId,
                    "The bot needs the Manage Webhooks permission in #" + channel.getName()
                            + " to post with player names and skins; posting as the bot until then");
        }
        try {
            return channel.retrieveWebhooks()
                    .submit()
                    .thenCompose(webhooks -> useOrCreate(api, channel, webhooks))
                    .exceptionallyCompose(failure -> botOnly(
                            channelId,
                            "Could not set up a webhook in #" + channel.getName() + " (" + Failures.describe(failure)
                                    + "); posting as the bot instead"));
        } catch (RuntimeException failure) {
            return botOnly(
                    channelId,
                    "Could not set up a webhook in #" + channel.getName() + " (" + Failures.describe(failure)
                            + "); posting as the bot instead");
        }
    }

    private CompletionStage<Optional<WebhookCredentials>> useOrCreate(
            JDA api, IWebhookContainer channel, List<Webhook> webhooks) {
        long channelId = channel.getIdLong();
        Optional<Webhook> ours =
                webhooks.stream().filter(webhook -> isOurs(api, webhook)).findFirst();
        if (ours.isPresent()) {
            return CompletableFuture.completedFuture(Optional.of(store(channelId, ours.get())));
        }
        if (webhooks.size() >= CHANNEL_WEBHOOK_LIMIT) {
            return botOnly(
                    channelId,
                    "#" + channel.getName() + " already has " + CHANNEL_WEBHOOK_LIMIT
                            + " webhooks; delete one so the bridge can add its own. Posting as the bot until then");
        }
        return channel.createWebhook(WEBHOOK_NAME)
                .submit()
                .thenApply(created -> Optional.of(store(channelId, created)));
    }

    private CompletableFuture<Void> execute(
            JDA api, WebhookCredentials webhook, OutboundMessage message, boolean mayRecreate) {
        rateLimits.bindWebhook(webhook.id(), message.channelId());
        try {
            return WebhookClient.createClient(api, Long.toUnsignedString(webhook.id()), webhook.token())
                    .sendMessage(Messages.create(message.text(), message.pingUserIds(), message.pingRoleIds()))
                    .setUsername(WebhookNames.sanitize(name(api, message.author())))
                    .setAvatarUrl(avatarUrl(api, message.author()))
                    .submit()
                    .<Void>thenApply(sent -> null)
                    .exceptionallyCompose(failure -> mayRecreate && isGone(failure)
                            ? recreate(api, message)
                            : CompletableFuture.failedFuture(failure));
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    private CompletableFuture<Void> recreate(JDA api, OutboundMessage message) {
        long channelId = message.channelId();
        known.remove(channelId);
        return repository
                .forget(channelId)
                .exceptionally(failure -> null)
                .thenCompose(ignored -> discover(api, channelId))
                .thenCompose(webhook ->
                        webhook.isPresent() ? execute(api, webhook.get(), message, false) : sendAsBot(api, message))
                .toCompletableFuture();
    }

    private CompletableFuture<Void> sendAsBot(JDA api, OutboundMessage message) {
        long channelId = message.channelId();
        GuildMessageChannel channel = api.getChannelById(GuildMessageChannel.class, channelId);
        if (channel == null) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("the bot cannot see channel " + Long.toUnsignedString(channelId)));
        }
        try {
            return channel.sendMessage(
                            Messages.create(DiscordText.asBot(message), message.pingUserIds(), message.pingRoleIds()))
                    .submit()
                    .thenApply(sent -> null);
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    private CompletionStage<Optional<WebhookCredentials>> botOnly(long channelId, String warning) {
        Long previous = botOnlyUntil.put(channelId, nanoClock.getAsLong() + FALLBACK_RETRY.toNanos());
        if (previous == null) {
            LOGGER.warn(warning);
        }
        return CompletableFuture.completedFuture(Optional.empty());
    }

    private WebhookCredentials store(long channelId, Webhook webhook) {
        WebhookCredentials credentials = new WebhookCredentials(webhook.getIdLong(), webhook.getToken());
        remember(channelId, credentials);
        var unused = repository.save(channelId, credentials).exceptionally(failure -> {
            LOGGER.warn(
                    "Could not save the webhook for channel {}: {}. Check that the data folder is writable",
                    channelId,
                    Failures.describe(failure));
            return null;
        });
        return credentials;
    }

    private WebhookCredentials remember(long channelId, WebhookCredentials credentials) {
        known.put(channelId, credentials);
        botOnlyUntil.remove(channelId);
        rateLimits.bindWebhook(credentials.id(), channelId);
        return credentials;
    }

    private static String name(JDA api, Author author) {
        return author.isServer() && author.name().isBlank() ? api.getSelfUser().getEffectiveName() : author.name();
    }

    private static @Nullable String avatarUrl(JDA api, Author author) {
        String avatar = author.avatarUrl();
        return author.isServer() && avatar == null ? api.getSelfUser().getEffectiveAvatarUrl() : avatar;
    }

    private static boolean isOurs(JDA api, Webhook webhook) {
        User owner = webhook.getOwnerAsUser();
        return webhook.getType() == WebhookType.INCOMING
                && webhook.getToken() != null
                && owner != null
                && owner.getIdLong() == api.getSelfUser().getIdLong();
    }

    private static boolean isGone(Throwable failure) {
        return Failures.unwrap(failure) instanceof ErrorResponseException response
                && (response.getErrorResponse() == ErrorResponse.UNKNOWN_WEBHOOK
                        || response.getErrorResponse() == ErrorResponse.INVALID_WEBHOOK_TOKEN);
    }
}
