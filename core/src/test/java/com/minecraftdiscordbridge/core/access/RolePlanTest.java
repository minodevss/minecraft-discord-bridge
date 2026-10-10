package com.minecraftdiscordbridge.core.access;

import static org.assertj.core.api.Assertions.assertThat;

import com.minecraftdiscordbridge.core.config.RoleSyncRule;
import com.minecraftdiscordbridge.core.config.SyncDirection;
import com.minecraftdiscordbridge.core.discord.MemberLookup;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RolePlanTest {
    private static final long USER = 666666666666666666L;
    private static final long VIP = 777777777777777777L;
    private static final long BOOSTER = 888888888888888888L;
    private static final long STAFF = 999999999999999999L;
    private static final RoleSyncRule VIP_TO_GROUP =
            new RoleSyncRule(Long.toUnsignedString(VIP), "VIP", SyncDirection.DISCORD_TO_MINECRAFT);
    private static final RoleSyncRule BOOSTER_TO_GROUP =
            new RoleSyncRule(Long.toUnsignedString(BOOSTER), "vip", SyncDirection.DISCORD_TO_MINECRAFT);
    private static final RoleSyncRule GROUP_TO_STAFF =
            new RoleSyncRule(Long.toUnsignedString(STAFF), "mod", SyncDirection.MINECRAFT_TO_DISCORD);

    @Test
    void addsTheGroupWhenTheMemberHasTheRole() {
        assertThat(RolePlan.sync(List.of(VIP_TO_GROUP), member(VIP), groups()))
                .containsExactly(new RoleChange.AddGroup("vip"));
    }

    @Test
    void removesTheGroupWhenTheMemberLacksTheRole() {
        assertThat(RolePlan.sync(List.of(VIP_TO_GROUP), member(), groups("vip")))
                .containsExactly(new RoleChange.RemoveGroup("vip"));
    }

    @Test
    void removesTheGroupWhenTheUserLeftTheDiscordServer() {
        assertThat(RolePlan.sync(List.of(VIP_TO_GROUP), new MemberLookup.NotMember(USER), groups("vip")))
                .containsExactly(new RoleChange.RemoveGroup("vip"));
    }

    @Test
    void changesNothingWhileDiscordIsUnreachable() {
        assertThat(RolePlan.sync(
                        List.of(VIP_TO_GROUP, GROUP_TO_STAFF), new MemberLookup.Unavailable("down"), groups("vip")))
                .isEmpty();
    }

    @Test
    void changesNothingWhenTheGroupsCannotBeRead() {
        assertThat(RolePlan.sync(List.of(VIP_TO_GROUP, GROUP_TO_STAFF), member(VIP), null))
                .isEmpty();
    }

    @Test
    void keepsAGroupThatAnyRuleStillWants() {
        assertThat(RolePlan.sync(List.of(VIP_TO_GROUP, BOOSTER_TO_GROUP), member(BOOSTER), groups("vip")))
                .isEmpty();
    }

    @Test
    void givesAndTakesDiscordRolesFromGroups() {
        assertThat(RolePlan.sync(List.of(GROUP_TO_STAFF), member(), groups("mod")))
                .containsExactly(new RoleChange.AddRole(STAFF));
        assertThat(RolePlan.sync(List.of(GROUP_TO_STAFF), member(STAFF), groups()))
                .containsExactly(new RoleChange.RemoveRole(STAFF));
        assertThat(RolePlan.sync(List.of(GROUP_TO_STAFF), new MemberLookup.NotMember(USER), groups("mod")))
                .isEmpty();
    }

    @Test
    void takesBackSyncedGroupsAndRolesOnUnlink() {
        assertThat(RolePlan.unlink(List.of(VIP_TO_GROUP, GROUP_TO_STAFF), member(VIP, STAFF), groups("vip", "mod")))
                .containsExactly(new RoleChange.RemoveGroup("vip"), new RoleChange.RemoveRole(STAFF));
    }

    private static MemberLookup member(Long... roles) {
        return new MemberLookup.Member(USER, Set.of(roles));
    }

    private static PlayerGroups groups(String... names) {
        return new PlayerGroups(Set.of(names), Set.of(names));
    }
}
