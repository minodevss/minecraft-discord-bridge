package com.minecraftdiscordbridge.core.access;

import com.minecraftdiscordbridge.core.config.RoleSyncRule;
import com.minecraftdiscordbridge.core.config.SyncDirection;
import com.minecraftdiscordbridge.core.discord.MemberLookup;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

final class RolePlan {
    private RolePlan() {}

    static List<RoleChange> sync(List<RoleSyncRule> rules, MemberLookup member, @Nullable PlayerGroups groups) {
        if (groups == null || member instanceof MemberLookup.Unavailable) {
            return List.of();
        }
        Map<String, Boolean> wantedGroups = new LinkedHashMap<>();
        Map<Long, Boolean> wantedRoles = new LinkedHashMap<>();
        Set<Long> roles = member instanceof MemberLookup.Member found ? found.roleIds() : Set.of();
        for (RoleSyncRule rule : rules) {
            long role = role(rule);
            String group = group(rule);
            if (rule.direction() == SyncDirection.DISCORD_TO_MINECRAFT) {
                wantedGroups.merge(group, roles.contains(role), Boolean::logicalOr);
            } else if (member instanceof MemberLookup.Member) {
                wantedRoles.merge(role, groups.inherited().contains(group), Boolean::logicalOr);
            }
        }
        List<RoleChange> changes = new ArrayList<>();
        wantedGroups.forEach((group, wanted) -> {
            boolean has = groups.direct().contains(group);
            if (wanted && !has) {
                changes.add(new RoleChange.AddGroup(group));
            } else if (!wanted && has) {
                changes.add(new RoleChange.RemoveGroup(group));
            }
        });
        wantedRoles.forEach((role, wanted) -> {
            boolean has = roles.contains(role);
            if (wanted && !has) {
                changes.add(new RoleChange.AddRole(role));
            } else if (!wanted && has) {
                changes.add(new RoleChange.RemoveRole(role));
            }
        });
        return List.copyOf(changes);
    }

    static List<RoleChange> unlink(List<RoleSyncRule> rules, MemberLookup member, @Nullable PlayerGroups groups) {
        Set<RoleChange> changes = new LinkedHashSet<>();
        for (RoleSyncRule rule : rules) {
            if (rule.direction() == SyncDirection.DISCORD_TO_MINECRAFT) {
                if (groups != null && groups.direct().contains(group(rule))) {
                    changes.add(new RoleChange.RemoveGroup(group(rule)));
                }
            } else if (member instanceof MemberLookup.Member found
                    && found.roleIds().contains(role(rule))) {
                changes.add(new RoleChange.RemoveRole(role(rule)));
            }
        }
        return List.copyOf(changes);
    }

    static long role(RoleSyncRule rule) {
        return Long.parseUnsignedLong(rule.discordRole());
    }

    static String group(RoleSyncRule rule) {
        return rule.group().toLowerCase(Locale.ROOT);
    }
}
