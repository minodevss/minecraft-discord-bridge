package com.minecraftdiscordbridge.core.discord.jda;

import static com.minecraftdiscordbridge.core.discord.jda.DiscordPayloads.FRIEND_ID;
import static com.minecraftdiscordbridge.core.discord.jda.DiscordPayloads.GUILD_ID;
import static com.minecraftdiscordbridge.core.discord.jda.DiscordPayloads.PLAYER_ID;
import static com.minecraftdiscordbridge.core.discord.jda.DiscordPayloads.ROLE_ID;
import static com.minecraftdiscordbridge.core.fakes.Waits.take;
import static org.assertj.core.api.Assertions.assertThat;

import com.minecraftdiscordbridge.core.discord.DiscordId;
import com.minecraftdiscordbridge.core.discord.MemberLookup;
import com.minecraftdiscordbridge.core.discord.RoleInfo;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import net.dv8tion.jda.api.utils.data.DataObject;
import org.junit.jupiter.api.Test;

class JdaGuildTest extends JdaTestBase {
    @Test
    void looksUpMemberRolesOverRestWithoutTheMembersIntent() throws Exception {
        bridge = startReady();

        MemberLookup found = bridge.member(PLAYER).get(5, TimeUnit.SECONDS);

        assertThat(found).isEqualTo(new MemberLookup.Member(PLAYER, Set.of(ROLE)));
        assertThat(discord.requests("GET", "guilds/" + GUILD_ID + "/members/" + PLAYER_ID))
                .hasSize(1);
        assertThat(discord.identifies.get(0).getInt("intents") & GUILD_MEMBERS_INTENT)
                .isZero();
    }

    @Test
    void reportsUsersWhoAreNotInTheServer() throws Exception {
        bridge = startReady();

        assertThat(bridge.member(FRIEND).get(5, TimeUnit.SECONDS)).isEqualTo(new MemberLookup.NotMember(FRIEND));
    }

    @Test
    void reportsMemberLookupsAsUnavailableBeforeConnecting() throws Exception {
        bridge = new JdaDiscord(
                TestSupport.settings(List.of()),
                discord.endpoints(),
                loop,
                listener,
                webhooks,
                commandHashes,
                TestSupport.ENGLISH);

        assertThat(bridge.member(PLAYER).get(5, TimeUnit.SECONDS)).isInstanceOf(MemberLookup.Unavailable.class);
        assertThat(bridge.roles()).isEmpty();
    }

    @Test
    void describesRolesWithModeratorPermissions() {
        bridge = startReady();

        Map<DiscordId, RoleInfo> roles = bridge.roles();

        assertThat(roles.get(GUILD).dangerousPermissions()).contains("Administrator");
        assertThat(roles.get(ROLE)).isEqualTo(new RoleInfo(ROLE, "Builders", Set.of(), false));
    }

    @Test
    void givesAndTakesRolesWithAnAuditLogReason() throws Exception {
        discord.botManagesRoles = true;
        bridge = startReady();
        String route = "guilds/" + GUILD_ID + "/members/" + PLAYER_ID + "/roles/" + ROLE_ID;

        assertThat(bridge.roles().get(ROLE).assignable()).isTrue();
        bridge.addRole(PLAYER, ROLE).get(5, TimeUnit.SECONDS);
        bridge.removeRole(PLAYER, ROLE).get(5, TimeUnit.SECONDS);

        assertThat(discord.requests("PUT", route))
                .singleElement()
                .satisfies(request -> assertThat(request.getHeaders().get("X-Audit-Log-Reason"))
                        .isNotBlank());
        assertThat(discord.requests("DELETE", route)).hasSize(1);
    }

    @Test
    void forwardsMemberRoleUpdatesAndDepartures() {
        bridge = startReady();

        discord.dispatch(
                "GUILD_MEMBER_UPDATE",
                DiscordPayloads.member("Alex")
                        .put("guild_id", Long.toString(GUILD_ID))
                        .put("user", DataObject.fromJson(DiscordPayloads.user(PLAYER_ID, "Alex", false))));
        discord.dispatch(
                "GUILD_MEMBER_REMOVE",
                DataObject.empty()
                        .put("guild_id", Long.toString(GUILD_ID))
                        .put("user", DataObject.fromJson(DiscordPayloads.user(FRIEND_ID, "Friend", false))));

        assertThat(take(listener.memberUpdates)).isEqualTo(new MemberLookup.Member(PLAYER, Set.of(ROLE)));
        assertThat(take(listener.membersLeft)).isEqualTo(FRIEND);
    }
}
