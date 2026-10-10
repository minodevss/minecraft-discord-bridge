package com.minecraftdiscordbridge.core.fakes;

import com.minecraftdiscordbridge.core.discord.AutocompleteReply;
import com.minecraftdiscordbridge.core.discord.AutocompleteRequest;
import com.minecraftdiscordbridge.core.discord.CommandRequest;
import com.minecraftdiscordbridge.core.discord.Discord;
import com.minecraftdiscordbridge.core.discord.DiscordId;
import com.minecraftdiscordbridge.core.discord.DiscordListener;
import com.minecraftdiscordbridge.core.discord.DiscordMessage;
import com.minecraftdiscordbridge.core.discord.DiscordSettings;
import com.minecraftdiscordbridge.core.discord.DiscordStatus;
import com.minecraftdiscordbridge.core.discord.MemberLookup;
import com.minecraftdiscordbridge.core.discord.OutboundMessage;
import com.minecraftdiscordbridge.core.discord.RoleInfo;
import com.minecraftdiscordbridge.core.loop.CoreLoop;
import com.minecraftdiscordbridge.core.loop.Deadline;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Function;

public final class RecordingDiscord implements Discord {
    public final DiscordSettings settings;
    public final BlockingQueue<OutboundMessage> sent = new LinkedBlockingQueue<>();
    public final List<String> presences = new CopyOnWriteArrayList<>();
    public final List<String> topics = new CopyOnWriteArrayList<>();
    public final List<OutboundMessage> finalMessages = new CopyOnWriteArrayList<>();
    public volatile Set<DiscordId> owners = Set.of();
    public volatile Duration shutdownTime = Duration.ZERO;
    public volatile boolean started;
    public volatile boolean shutDown;
    public volatile DiscordStatus.State state = DiscordStatus.State.READY;
    public volatile Function<DiscordId, CompletableFuture<MemberLookup>> members =
            userId -> CompletableFuture.completedFuture(new MemberLookup.NotMember(userId));
    public final Map<DiscordId, RoleInfo> roles = new ConcurrentHashMap<>();
    public final List<String> roleChanges = new CopyOnWriteArrayList<>();
    public final List<DiscordId> memberLookups = new CopyOnWriteArrayList<>();
    private final CoreLoop loop;
    private final DiscordListener listener;

    public RecordingDiscord(DiscordSettings settings, CoreLoop loop, DiscordListener listener) {
        this.settings = settings;
        this.loop = loop;
        this.listener = listener;
    }

    public RecordingDiscord() {
        this(null, null, null);
    }

    public void memberWithRoles(DiscordId userId, DiscordId... roleIds) {
        MemberLookup.Member member = new MemberLookup.Member(userId, Set.of(roleIds));
        members = id -> CompletableFuture.completedFuture(id.equals(userId) ? member : new MemberLookup.NotMember(id));
    }

    public void unreachable() {
        members = id -> CompletableFuture.completedFuture(new MemberLookup.Unavailable("Discord is down"));
    }

    public void role(DiscordId id, String name, String... dangerous) {
        roles.put(id, new RoleInfo(id, name, Set.of(dangerous), true));
    }

    public List<OutboundMessage> sentMessages() {
        return List.copyOf(sent);
    }

    public List<String> texts() {
        return sent.stream().map(OutboundMessage::text).toList();
    }

    public List<String> awaitTexts(int count) {
        Waits.until(count + " messages", () -> sent.size() >= count);
        return texts();
    }

    public void receive(DiscordMessage message) {
        loop.content(() -> listener.onMessage(message));
    }

    public RecordingReply command(CommandRequest request) {
        RecordingReply reply = new RecordingReply();
        loop.control(() -> listener.onCommand(request, reply));
        return reply;
    }

    public void autocomplete(AutocompleteRequest request, AutocompleteReply reply) {
        loop.control(() -> listener.onAutocomplete(request, reply));
    }

    public void memberUpdated(MemberLookup.Member member) {
        loop.control(() -> listener.onMemberUpdated(member));
    }

    public void statusChanged() {
        loop.control(() -> listener.onStatusChanged(status()));
    }

    public OutboundMessage nextSent() {
        return Waits.take(sent);
    }

    @Override
    public void start() {
        started = true;
    }

    @Override
    public DiscordStatus status() {
        return new DiscordStatus(shutDown ? DiscordStatus.State.STOPPED : state, Optional.empty(), 42, Map.of());
    }

    @Override
    public void send(OutboundMessage message) {
        sent.add(message);
    }

    @Override
    public void updatePresence(String text) {
        presences.add(text);
    }

    @Override
    public void updateTopic(DiscordId channelId, String topic) {
        topics.add(channelId + ":" + topic);
    }

    @Override
    public boolean isApplicationOwner(DiscordId userId) {
        return owners.contains(userId);
    }

    @Override
    public CompletableFuture<MemberLookup> member(DiscordId userId) {
        memberLookups.add(userId);
        return members.apply(userId);
    }

    @Override
    public Map<DiscordId, RoleInfo> roles() {
        return Map.copyOf(roles);
    }

    @Override
    public CompletableFuture<Void> addRole(DiscordId userId, DiscordId roleId) {
        roleChanges.add("+" + roleId + " " + userId);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> removeRole(DiscordId userId, DiscordId roleId) {
        roleChanges.add("-" + roleId + " " + userId);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void shutdown(List<OutboundMessage> finals, Deadline deadline) {
        finalMessages.addAll(finals);
        try {
            Thread.sleep(shutdownTime.toMillis());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        shutDown = true;
    }
}
