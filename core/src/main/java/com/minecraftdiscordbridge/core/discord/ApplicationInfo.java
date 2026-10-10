package com.minecraftdiscordbridge.core.discord;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.dv8tion.jda.api.utils.data.DataArray;
import net.dv8tion.jda.api.utils.data.DataObject;
import org.jspecify.annotations.Nullable;

record ApplicationInfo(
        long id,
        long flags,
        boolean botPublic,
        long ownerId,
        @Nullable Team team) {
    static final long GATEWAY_GUILD_MEMBERS = 1L << 14;
    static final long GATEWAY_GUILD_MEMBERS_LIMITED = 1L << 15;
    static final long GATEWAY_MESSAGE_CONTENT = 1L << 18;
    static final long GATEWAY_MESSAGE_CONTENT_LIMITED = 1L << 19;
    private static final int ACCEPTED = 2;
    private static final Set<String> OWNER_ROLES = Set.of("admin", "developer");

    record Team(long ownerUserId, List<Member> members) {
        Team {
            members = List.copyOf(members);
        }
    }

    record Member(long userId, String role, int membershipState) {}

    static ApplicationInfo parse(DataObject json) {
        long owner = json.optObject("owner")
                .map(user -> user.getUnsignedLong("id", 0L))
                .orElse(0L);
        Team team = json.isNull("team") ? null : parseTeam(json.getObject("team"));
        return new ApplicationInfo(
                json.getUnsignedLong("id"), json.getLong("flags", 0L), json.getBoolean("bot_public"), owner, team);
    }

    private static Team parseTeam(DataObject json) {
        DataArray array = json.optArray("members").orElseGet(DataArray::empty);
        List<Member> members = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            DataObject member = array.getObject(i);
            members.add(new Member(
                    member.getObject("user").getUnsignedLong("id"),
                    member.getString("role", ""),
                    member.getInt("membership_state", 0)));
        }
        return new Team(json.getUnsignedLong("owner_user_id", 0L), members);
    }

    boolean messageContentGranted() {
        return (flags & (GATEWAY_MESSAGE_CONTENT | GATEWAY_MESSAGE_CONTENT_LIMITED)) != 0;
    }

    boolean membersGranted() {
        return (flags & (GATEWAY_GUILD_MEMBERS | GATEWAY_GUILD_MEMBERS_LIMITED)) != 0;
    }

    ApplicationInfo withFlags(long newFlags) {
        return new ApplicationInfo(id, newFlags, botPublic, ownerId, team);
    }

    boolean isOwner(long userId) {
        Team owners = team;
        if (owners == null) {
            return userId != 0 && userId == ownerId;
        }
        return userId == owners.ownerUserId()
                || owners.members().stream()
                        .anyMatch(member -> member.userId() == userId
                                && member.membershipState() == ACCEPTED
                                && OWNER_ROLES.contains(member.role()));
    }

    String botSettingsUrl() {
        return "https://discord.com/developers/applications/" + Long.toUnsignedString(id) + "/bot";
    }
}
