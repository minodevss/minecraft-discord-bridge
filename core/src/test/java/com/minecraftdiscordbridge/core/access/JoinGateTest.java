package com.minecraftdiscordbridge.core.access;

import static org.assertj.core.api.Assertions.assertThat;

import com.minecraftdiscordbridge.core.codes.Codes;
import com.minecraftdiscordbridge.core.config.LinkingConfig;
import com.minecraftdiscordbridge.core.config.UnreachablePolicy;
import com.minecraftdiscordbridge.core.discord.DiscordId;
import com.minecraftdiscordbridge.core.discord.MemberLookup;
import com.minecraftdiscordbridge.core.fakes.FakeGroups;
import com.minecraftdiscordbridge.core.fakes.MutableClock;
import com.minecraftdiscordbridge.core.fakes.RecordingDiscord;
import com.minecraftdiscordbridge.core.i18n.Lang;
import com.minecraftdiscordbridge.core.i18n.Language;
import com.minecraftdiscordbridge.core.i18n.Replies;
import com.minecraftdiscordbridge.core.linking.PendingLinks;
import com.minecraftdiscordbridge.core.platform.JoinDecision;
import com.minecraftdiscordbridge.core.platform.Permission;
import com.minecraftdiscordbridge.core.platform.PlatformInfo;
import com.minecraftdiscordbridge.core.platform.PlayerProfile;
import com.minecraftdiscordbridge.core.storage.Edition;
import com.minecraftdiscordbridge.core.storage.Link;
import com.minecraftdiscordbridge.core.storage.LinkStore;
import com.minecraftdiscordbridge.core.text.TextRenderer;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JoinGateTest {
    private static final PlatformInfo ONLINE = new PlatformInfo("Test", "1.21.1", 3955, true);
    private static final PlayerProfile STEVE =
            new PlayerProfile(UUID.fromString("00000000-0000-4000-8000-000000000001"), "Steve", "Steve", null, false);
    private static final DiscordId STEVE_DISCORD = new DiscordId(666666666666666666L);
    private static final DiscordId VIP = new DiscordId(777777777777777777L);
    private static final Pattern CODE = Pattern.compile("/link ([" + Codes.ALPHABET + "]{" + Codes.LENGTH + "})");

    @TempDir
    Path directory;

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-10T00:00:00Z"));
    private final RecordingDiscord discord = new RecordingDiscord();
    private final FakeGroups groups = new FakeGroups();
    private final AccessMemory memory = new AccessMemory();
    private final PendingLinks pending = new PendingLinks(new Codes(new Random(7)));
    private LinkStore links;
    private AccessContext context;

    @BeforeEach
    void openStore() {
        links = LinkStore.open(directory);
        context = new AccessContext(
                links, pending, discord, groups, memory, clock, Runnable::run, (delay, task) -> task.run());
    }

    @Test
    void letsEveryoneInWithoutTouchingDiscordWhenTheGateIsOff() {
        JoinGate gate = gate(new LinkingConfig(
                true, false, Optional.of(new DiscordId(777777777777777777L)), UnreachablePolicy.DENY));

        assertThat(decide(gate, STEVE)).isInstanceOf(JoinDecision.Allow.class);
        assertThat(discord.memberLookups).isEmpty();
    }

    @Test
    void showsUnlinkedPlayersAFreshCodeThatLinkingAccepts() {
        JoinGate gate = gate(required(Optional.empty(), UnreachablePolicy.DENY));

        String screen = denied(decide(gate, STEVE));

        assertThat(screen)
                .startsWith("This server needs a linked Discord account.")
                .contains("then rejoin");
        Optional<PendingLinks.Pending> redeemed = pending.redeem(code(screen), clock.instant());
        assertThat(redeemed).map(PendingLinks.Pending::playerId).contains(STEVE.id());
    }

    @Test
    void letsLinkedPlayersInWithoutAskingDiscordWhenNoRoleIsRequired() throws IOException {
        link();
        JoinGate gate = gate(required(Optional.empty(), UnreachablePolicy.DENY));

        assertThat(decide(gate, STEVE)).isInstanceOf(JoinDecision.Allow.class);
        assertThat(discord.memberLookups).isEmpty();
    }

    @Test
    void letsLinkedMembersWithTheRequiredRoleIn() throws IOException {
        link();
        discord.memberWithRoles(STEVE_DISCORD, VIP);

        assertThat(decide(roleGate(UnreachablePolicy.DENY), STEVE)).isInstanceOf(JoinDecision.Allow.class);
        assertThat(discord.memberLookups).containsExactly(STEVE_DISCORD);
    }

    @Test
    void namesTheMissingRoleOnTheKickScreen() throws IOException {
        link();
        discord.memberWithRoles(STEVE_DISCORD, new DiscordId(1));
        discord.role(VIP, "VIP");

        assertThat(denied(decide(roleGate(UnreachablePolicy.DENY), STEVE)))
                .isEqualTo("You need the VIP role in the Discord server to join.");
    }

    @Test
    void keepsOutLinkedPlayersWhoLeftTheDiscordServer() throws IOException {
        link();

        assertThat(denied(decide(roleGate(UnreachablePolicy.ALLOW), STEVE))).contains("not in the Discord server");
    }

    @Test
    void reusesKnownRolesForAMinuteThenAsksAgain() throws IOException {
        link();
        discord.memberWithRoles(STEVE_DISCORD, VIP);
        JoinGate gate = roleGate(UnreachablePolicy.DENY);

        decide(gate, STEVE);
        clock.advance(Duration.ofSeconds(59));
        decide(gate, STEVE);
        assertThat(discord.memberLookups).hasSize(1);

        clock.advance(Duration.ofSeconds(2));
        decide(gate, STEVE);
        assertThat(discord.memberLookups).hasSize(2);
    }

    @Test
    void deniesWhenDiscordIsUnreachableAndThePlayerWasNotLetInRecently() throws IOException {
        link();
        discord.unreachable();

        assertThat(denied(decide(roleGate(UnreachablePolicy.DENY), STEVE)))
                .startsWith("Your Discord account cannot be checked right now.");
    }

    @Test
    void letsRecentPlayersInForADayWhileDiscordIsUnreachable() throws IOException {
        link();
        discord.memberWithRoles(STEVE_DISCORD, VIP);
        JoinGate gate = roleGate(UnreachablePolicy.DENY);
        decide(gate, STEVE);
        discord.unreachable();

        clock.advance(Duration.ofHours(23));
        assertThat(decide(gate, STEVE)).isInstanceOf(JoinDecision.Allow.class);

        clock.advance(Duration.ofHours(2));
        assertThat(decide(gate, STEVE)).isInstanceOf(JoinDecision.Deny.class);
    }

    @Test
    void letsLinkedPlayersInWhileDiscordIsUnreachableWhenThePolicyAllows() throws IOException {
        link();
        discord.unreachable();

        assertThat(decide(roleGate(UnreachablePolicy.ALLOW), STEVE)).isInstanceOf(JoinDecision.Allow.class);
    }

    @Test
    void stillShowsTheLinkCodeWhileDiscordIsUnreachable() {
        discord.unreachable();

        assertThat(denied(decide(roleGate(UnreachablePolicy.ALLOW), STEVE))).contains("/link ");
    }

    @Test
    void letsPlayersWithTheBypassPermissionIn() {
        JoinGate gate = roleGate(UnreachablePolicy.DENY);
        CompletableFuture<JoinDecision> bypassed = new CompletableFuture<>();
        gate.decide(STEVE, true, bypassed);
        groups.permissions.put(STEVE.id(), Set.of(Permission.GATE_BYPASS.node()));

        assertThat(bypassed.getNow(null)).isInstanceOf(JoinDecision.Allow.class);
        assertThat(decide(gate, STEVE)).isInstanceOf(JoinDecision.Allow.class);
        assertThat(discord.memberLookups).isEmpty();
    }

    @Test
    void staysOffOnOfflineModeServersWithoutSecureForwarding() {
        PlatformInfo offline = new PlatformInfo("Test", "1.21.1", 3955, false, false);
        JoinGate gate =
                JoinGate.create(required(Optional.empty(), UnreachablePolicy.DENY), offline, context, replies());

        assertThat(gate.active()).isFalse();
        assertThat(decide(gate, STEVE)).isInstanceOf(JoinDecision.Allow.class);
    }

    @Test
    void runsBehindSecureProxyForwarding() {
        PlatformInfo forwarded = new PlatformInfo("Test", "1.21.1", 3955, false, true);

        assertThat(JoinGate.create(required(Optional.empty(), UnreachablePolicy.DENY), forwarded, context, replies())
                        .active())
                .isTrue();
    }

    @Test
    void staysOffWhenLinkingIsTurnedOff() {
        JoinGate gate = gate(new LinkingConfig(false, true, Optional.empty(), UnreachablePolicy.DENY));

        assertThat(gate.active()).isFalse();
    }

    @Test
    void fallsBackToTheUnreachablePolicyWhenTheCheckTakesTooLong() throws IOException {
        assertThat(roleGate(UnreachablePolicy.ALLOW).fallback(STEVE, false)).isInstanceOf(JoinDecision.Deny.class);

        link();
        assertThat(roleGate(UnreachablePolicy.ALLOW).fallback(STEVE, false)).isInstanceOf(JoinDecision.Allow.class);
        assertThat(roleGate(UnreachablePolicy.DENY).fallback(STEVE, false)).isInstanceOf(JoinDecision.Deny.class);
        assertThat(gate(required(Optional.empty(), UnreachablePolicy.DENY)).fallback(STEVE, false))
                .isInstanceOf(JoinDecision.Allow.class);
    }

    @Test
    void waitsForDiscordOnTheLoopAndAnswersOnce() throws IOException {
        link();
        CompletableFuture<MemberLookup> answer = new CompletableFuture<>();
        discord.members = id -> answer;
        CompletableFuture<JoinDecision> decision = new CompletableFuture<>();

        roleGate(UnreachablePolicy.DENY).decide(STEVE, false, decision);
        assertThat(decision).isNotDone();

        answer.complete(new MemberLookup.Member(STEVE_DISCORD, Set.of(VIP)));
        assertThat(decision.getNow(null)).isInstanceOf(JoinDecision.Allow.class);
    }

    private JoinGate roleGate(UnreachablePolicy policy) {
        return gate(required(Optional.of(VIP), policy));
    }

    private JoinGate gate(LinkingConfig config) {
        return JoinGate.create(config, ONLINE, context, replies());
    }

    private static LinkingConfig required(Optional<DiscordId> role, UnreachablePolicy policy) {
        return new LinkingConfig(true, true, role, policy);
    }

    private static Replies replies() {
        return new Replies(Lang.of(Language.EN), 3955);
    }

    private void link() throws IOException {
        links.link(new Link(STEVE.id(), STEVE_DISCORD, "Steve", Edition.JAVA, clock.instant()));
    }

    private static JoinDecision decide(JoinGate gate, PlayerProfile player) {
        CompletableFuture<JoinDecision> decision = new CompletableFuture<>();
        gate.decide(player, false, decision);
        assertThat(decision).isDone();
        return decision.join();
    }

    private static String denied(JoinDecision decision) {
        assertThat(decision).isInstanceOf(JoinDecision.Deny.class);
        return TextRenderer.plain(TextRenderer.fromJson(((JoinDecision.Deny) decision).json()));
    }

    private static String code(String screen) {
        Matcher matcher = CODE.matcher(screen);
        assertThat(matcher.find()).as(screen).isTrue();
        return matcher.group(1);
    }
}
