package com.minecraftdiscordbridge.core.discord.jda;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import net.dv8tion.jda.api.utils.data.DataObject;
import org.junit.jupiter.api.Test;

class ApplicationInfoTest {
    @Test
    void readsTheApplicationFromDiscordJson() {
        ApplicationInfo info = ApplicationInfo.parse(DataObject.fromJson("""
                {"id":"123","flags":524288,"bot_public":true,"owner":{"id":"456"},
                 "team":{"owner_user_id":"789","members":[
                   {"membership_state":2,"role":"admin","user":{"id":"11"}},
                   {"membership_state":1,"role":"developer","user":{"id":"12"}}]}}
                """));

        assertThat(info.id()).isEqualTo(123);
        assertThat(info.botPublic()).isTrue();
        assertThat(info.ownerId()).isEqualTo(456);
        assertThat(info.team()).isNotNull();
        assertThat(info.team().ownerUserId()).isEqualTo(789);
        assertThat(info.team().members())
                .containsExactly(
                        new ApplicationInfo.Member(11, "admin", 2), new ApplicationInfo.Member(12, "developer", 1));
    }

    @Test
    void readsAnApplicationWithoutATeam() {
        ApplicationInfo info = ApplicationInfo.parse(DataObject.fromJson(
                "{\"id\":\"123\",\"flags\":0,\"bot_public\":false,\"owner\":{\"id\":\"456\"},\"team\":null}"));

        assertThat(info.team()).isNull();
        assertThat(info.isOwner(456)).isTrue();
        assertThat(info.isOwner(457)).isFalse();
    }

    @Test
    void grantsMessageContentWithEitherFlag() {
        assertThat(withFlags(1L << 18).messageContentGranted()).isTrue();
        assertThat(withFlags(1L << 19).messageContentGranted()).isTrue();
        assertThat(withFlags(1L << 14).messageContentGranted()).isFalse();
    }

    @Test
    void grantsServerMembersWithEitherFlag() {
        assertThat(withFlags(1L << 14).membersGranted()).isTrue();
        assertThat(withFlags(1L << 15).membersGranted()).isTrue();
        assertThat(withFlags(1L << 19).membersGranted()).isFalse();
    }

    @Test
    void countsTheTeamOwnerAndAcceptedAdminsAndDevelopersAsOwners() {
        ApplicationInfo info = new ApplicationInfo(
                1,
                0,
                false,
                99,
                new ApplicationInfo.Team(
                        10,
                        List.of(
                                new ApplicationInfo.Member(11, "admin", 2),
                                new ApplicationInfo.Member(12, "developer", 2),
                                new ApplicationInfo.Member(13, "read_only", 2),
                                new ApplicationInfo.Member(14, "admin", 1))));

        assertThat(info.isOwner(10)).isTrue();
        assertThat(info.isOwner(11)).isTrue();
        assertThat(info.isOwner(12)).isTrue();
        assertThat(info.isOwner(13)).isFalse();
        assertThat(info.isOwner(14)).isFalse();
        assertThat(info.isOwner(99)).isFalse();
    }

    private static ApplicationInfo withFlags(long flags) {
        return new ApplicationInfo(1, flags, false, 2, null);
    }
}
