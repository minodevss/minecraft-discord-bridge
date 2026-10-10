package com.minecraftdiscordbridge.core.discord.jda;

import com.minecraftdiscordbridge.core.discord.Author;
import com.minecraftdiscordbridge.core.discord.AutocompleteReply;
import com.minecraftdiscordbridge.core.discord.AutocompleteRequest;
import com.minecraftdiscordbridge.core.discord.CommandHashRepository;
import com.minecraftdiscordbridge.core.discord.CommandReply;
import com.minecraftdiscordbridge.core.discord.CommandRequest;
import com.minecraftdiscordbridge.core.discord.CommandSpec;
import com.minecraftdiscordbridge.core.discord.DiscordId;
import com.minecraftdiscordbridge.core.discord.DiscordListener;
import com.minecraftdiscordbridge.core.discord.DiscordMessage;
import com.minecraftdiscordbridge.core.discord.DiscordSettings;
import com.minecraftdiscordbridge.core.discord.DiscordStatus;
import com.minecraftdiscordbridge.core.discord.MemberLookup;
import com.minecraftdiscordbridge.core.discord.WebhookCredentials;
import com.minecraftdiscordbridge.core.discord.WebhookRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

final class TestSupport {
    private TestSupport() {}

    static DiscordSettings settings(List<CommandSpec> commands) {
        return new DiscordSettings(
                FakeDiscord.TOKEN,
                Optional.of(new DiscordId(FakeDiscord.GUILD_ID)),
                Set.of(new DiscordId(FakeDiscord.CHANNEL_ID)),
                Author.server("Survival", Optional.of("https://example.com/server.png")),
                true,
                false,
                commands);
    }

    static final class RecordingListener implements DiscordListener {
        final BlockingQueue<DiscordStatus> statuses = new LinkedBlockingQueue<>();
        final BlockingQueue<DiscordMessage> messages = new LinkedBlockingQueue<>();
        final BlockingQueue<CommandRequest> commands = new LinkedBlockingQueue<>();
        final BlockingQueue<CommandReply> replies = new LinkedBlockingQueue<>();
        final BlockingQueue<AutocompleteRequest> autocompletes = new LinkedBlockingQueue<>();
        final BlockingQueue<AutocompleteReply> autocompleteReplies = new LinkedBlockingQueue<>();
        final BlockingQueue<MemberLookup.Member> memberUpdates = new LinkedBlockingQueue<>();
        final BlockingQueue<DiscordId> membersLeft = new LinkedBlockingQueue<>();

        @Override
        public void onStatusChanged(DiscordStatus status) {
            statuses.add(status);
        }

        @Override
        public void onMessage(DiscordMessage message) {
            messages.add(message);
        }

        @Override
        public void onCommand(CommandRequest request, CommandReply reply) {
            commands.add(request);
            replies.add(reply);
        }

        @Override
        public void onAutocomplete(AutocompleteRequest request, AutocompleteReply reply) {
            autocompletes.add(request);
            autocompleteReplies.add(reply);
        }

        @Override
        public void onMemberUpdated(MemberLookup.Member member) {
            memberUpdates.add(member);
        }

        @Override
        public void onMemberLeft(DiscordId userId) {
            membersLeft.add(userId);
        }
    }

    static final class MemoryWebhooks implements WebhookRepository {
        final Map<DiscordId, WebhookCredentials> stored = new ConcurrentHashMap<>();

        @Override
        public CompletionStage<Optional<WebhookCredentials>> find(DiscordId channelId) {
            return CompletableFuture.completedFuture(Optional.ofNullable(stored.get(channelId)));
        }

        @Override
        public CompletionStage<Void> save(DiscordId channelId, WebhookCredentials webhook) {
            stored.put(channelId, webhook);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> forget(DiscordId channelId) {
            stored.remove(channelId);
            return CompletableFuture.completedFuture(null);
        }
    }

    static final class MemoryCommandHashes implements CommandHashRepository {
        final Map<DiscordId, String> stored = new ConcurrentHashMap<>();

        @Override
        public CompletionStage<Optional<String>> find(DiscordId guildId) {
            return CompletableFuture.completedFuture(Optional.ofNullable(stored.get(guildId)));
        }

        @Override
        public CompletionStage<Void> save(DiscordId guildId, String hash) {
            stored.put(guildId, hash);
            return CompletableFuture.completedFuture(null);
        }
    }
}
