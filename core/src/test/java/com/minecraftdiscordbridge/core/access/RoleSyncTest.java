package com.minecraftdiscordbridge.core.access;

import static org.assertj.core.api.Assertions.assertThat;

import com.minecraftdiscordbridge.core.FakeGroups;
import com.minecraftdiscordbridge.core.MutableClock;
import com.minecraftdiscordbridge.core.config.RoleSyncConfig;
import com.minecraftdiscordbridge.core.config.RoleSyncRule;
import com.minecraftdiscordbridge.core.config.SyncDirection;
import com.minecraftdiscordbridge.core.discord.MemberLookup;
import com.minecraftdiscordbridge.core.discord.RoleInfo;
import com.minecraftdiscordbridge.core.linking.Codes;
import com.minecraftdiscordbridge.core.linking.PendingLinks;
import com.minecraftdiscordbridge.core.platform.PlatformInfo;
import com.minecraftdiscordbridge.core.platform.PlayerProfile;
import com.minecraftdiscordbridge.core.storage.Edition;
import com.minecraftdiscordbridge.core.storage.Link;
import com.minecraftdiscordbridge.core.storage.LinkChange;
import com.minecraftdiscordbridge.core.storage.LinkStore;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RoleSyncTest {
    private static final PlatformInfo ONLINE = new PlatformInfo("Test", "1.21.1", 3955, true);
    private static final PlayerProfile STEVE =
            new PlayerProfile(UUID.fromString("00000000-0000-4000-8000-000000000001"), "Steve", "Steve", null, false);
    private static final long USER = 666666666666666666L;
    private static final long VIP = 777777777777777777L;
    private static final long STAFF = 888888888888888888L;
    private static final RoleSyncRule VIP_TO_GROUP =
            new RoleSyncRule(Long.toUnsignedString(VIP), "vip", SyncDirection.DISCORD_TO_MINECRAFT);
    private static final RoleSyncRule ADMIN_TO_STAFF =
            new RoleSyncRule(Long.toUnsignedString(STAFF), "admin", SyncDirection.MINECRAFT_TO_DISCORD);

    @TempDir
    Path directory;

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-10T00:00:00Z"));
    private final StubDiscord discord = new StubDiscord();
    private final FakeGroups groups = new FakeGroups();
    private LinkStore links;
    private AccessContext context;

    @BeforeEach
    void setUp() throws IOException {
        links = LinkStore.open(directory);
        context = new AccessContext(
                links,
                new PendingLinks(new Codes(new Random(7))),
                discord,
                groups,
                new AccessMemory(),
                clock,
                Runnable::run);
        links.link(new Link(STEVE.id(), Long.toUnsignedString(USER), "Steve", Edition.JAVA, clock.instant()));
        groups.groups.put("vip", new GroupCheck.Safe());
        groups.groups.put("admin", new GroupCheck.Dangerous("*"));
        discord.role(VIP, "VIP");
        discord.role(STAFF, "Staff");
    }

    @Test
    void givesTheGroupToLinkedMembersWithTheRoleWhenTheyJoin() {
        discord.memberWithRoles(USER, VIP);
        RoleSync sync = ready(VIP_TO_GROUP);

        sync.playerJoined(STEVE);

        assertThat(groups.changes).containsExactly("+vip " + STEVE.id());
    }

    @Test
    void takesTheGroupWhenADiscordRoleUpdateRemovesTheRole() {
        groups.members.put(STEVE.id(), Set.of("vip"));
        RoleSync sync = ready(VIP_TO_GROUP);

        sync.memberUpdated(new MemberLookup.Member(USER, Set.of()));

        assertThat(groups.changes).containsExactly("-vip " + STEVE.id());
        assertThat(discord.lookups).isEmpty();
    }

    @Test
    void syncsRightAfterLinkingAndTakesBackOnUnlink() {
        discord.memberWithRoles(USER, VIP);
        RoleSync sync = ready(VIP_TO_GROUP);
        Link link = links.byMinecraftId(STEVE.id()).orElseThrow();

        sync.linkChanged(new LinkChange.Linked(link));
        sync.linkChanged(new LinkChange.Unlinked(link));

        assertThat(groups.changes).containsExactly("+vip " + STEVE.id(), "-vip " + STEVE.id());
    }

    @Test
    void refusesGroupsThatGrantOperatorPowerToDiscordRoles() {
        RoleSyncRule adminFromDiscord =
                new RoleSyncRule(Long.toUnsignedString(VIP), "admin", SyncDirection.DISCORD_TO_MINECRAFT);
        discord.memberWithRoles(USER, VIP);
        RoleSync sync = ready(adminFromDiscord);

        sync.playerJoined(STEVE);

        assertThat(sync.activeRules()).isEmpty();
        assertThat(groups.changes).isEmpty();
    }

    @Test
    void refusesDiscordRolesWithModeratorPermissions() {
        discord.role(STAFF, "Staff", "Administrator");
        groups.members.put(STEVE.id(), Set.of("admin"));
        discord.memberWithRoles(USER);
        RoleSync sync = ready(ADMIN_TO_STAFF);

        sync.playerJoined(STEVE);

        assertThat(sync.activeRules()).isEmpty();
        assertThat(discord.roleChanges).isEmpty();
    }

    @Test
    void givesSafeDiscordRolesToMembersOfAGroup() {
        groups.members.put(STEVE.id(), Set.of("admin"));
        discord.memberWithRoles(USER);
        RoleSync sync = ready(ADMIN_TO_STAFF);

        sync.playerJoined(STEVE);

        assertThat(sync.activeRules()).containsExactly(ADMIN_TO_STAFF);
        assertThat(discord.roleChanges).containsExactly("+" + STAFF);
    }

    @Test
    void refusesRolesTheBotCannotAssign() {
        discord.roles.put(STAFF, new RoleInfo(STAFF, "Staff", Set.of(), false));

        assertThat(ready(ADMIN_TO_STAFF).activeRules()).isEmpty();
    }

    @Test
    void refusesMissingGroupsAndRoles() {
        RoleSyncRule missingGroup =
                new RoleSyncRule(Long.toUnsignedString(VIP), "nobody", SyncDirection.DISCORD_TO_MINECRAFT);
        RoleSyncRule missingRole = new RoleSyncRule("123456789012345678", "vip", SyncDirection.DISCORD_TO_MINECRAFT);

        assertThat(ready(missingGroup, missingRole).activeRules()).isEmpty();
    }

    @Test
    void waitsForDiscordBeforeUsingAnyRule() {
        RoleSync sync = RoleSync.create(new RoleSyncConfig(List.of(VIP_TO_GROUP)), ONLINE, context);

        assertThat(sync.activeRules()).isEmpty();
        sync.discordReady();
        assertThat(sync.activeRules()).containsExactly(VIP_TO_GROUP);
    }

    @Test
    void staysOffOnOfflineModeServersWithoutSecureForwarding() {
        PlatformInfo offline = new PlatformInfo("Test", "1.21.1", 3955, false, false);
        RoleSync sync = RoleSync.create(new RoleSyncConfig(List.of(VIP_TO_GROUP)), offline, context);
        sync.discordReady();

        assertThat(sync.activeRules()).isEmpty();
    }

    @Test
    void staysOffWithoutLuckPerms() {
        groups.present = false;
        RoleSync sync = ready(VIP_TO_GROUP);

        assertThat(sync.activeRules()).isEmpty();
    }

    @Test
    void usesOnlyOneDirectionWhenARulePairSyncsBothWays() {
        RoleSyncRule back = new RoleSyncRule(Long.toUnsignedString(VIP), "VIP", SyncDirection.MINECRAFT_TO_DISCORD);

        assertThat(ready(VIP_TO_GROUP, back).activeRules()).containsExactly(VIP_TO_GROUP);
    }

    private RoleSync ready(RoleSyncRule... rules) {
        RoleSync sync = RoleSync.create(new RoleSyncConfig(List.of(rules)), ONLINE, context);
        sync.discordReady();
        return sync;
    }
}
