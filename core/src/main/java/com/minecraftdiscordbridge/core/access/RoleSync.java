package com.minecraftdiscordbridge.core.access;

import com.minecraftdiscordbridge.core.config.RoleSyncConfig;
import com.minecraftdiscordbridge.core.config.RoleSyncRule;
import com.minecraftdiscordbridge.core.config.SyncDirection;
import com.minecraftdiscordbridge.core.discord.DiscordId;
import com.minecraftdiscordbridge.core.discord.MemberLookup;
import com.minecraftdiscordbridge.core.discord.RoleInfo;
import com.minecraftdiscordbridge.core.platform.PlatformInfo;
import com.minecraftdiscordbridge.core.platform.PlayerProfile;
import com.minecraftdiscordbridge.core.storage.Link;
import com.minecraftdiscordbridge.core.storage.LinkChange;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RoleSync {
    private static final Logger LOGGER = LoggerFactory.getLogger("MinecraftDiscordBridge");

    private enum Check {
        PENDING,
        PASSED,
        REFUSED
    }

    private static final class RuleState {
        private final RoleSyncRule rule;
        private Check group = Check.PENDING;
        private Check role = Check.PENDING;

        private RuleState(RoleSyncRule rule) {
            this.rule = rule;
        }

        private boolean active() {
            return group == Check.PASSED && role == Check.PASSED;
        }

        private boolean grantsGroup() {
            return rule.direction() == SyncDirection.DISCORD_TO_MINECRAFT;
        }
    }

    @FunctionalInterface
    private interface Planner {
        List<RoleChange> plan(List<RoleSyncRule> rules, MemberLookup member, @Nullable PlayerGroups groups);
    }

    private final List<RuleState> rules;
    private final AccessContext context;

    private RoleSync(List<RuleState> rules, AccessContext context) {
        this.rules = rules;
        this.context = context;
    }

    public static RoleSync create(RoleSyncConfig config, PlatformInfo info, AccessContext context) {
        if (config.rules().isEmpty()) {
            return new RoleSync(List.of(), context);
        }
        if (!info.verifiesPlayers()) {
            LOGGER.warn("Role sync stays off because {}", Offline.WHY);
            return new RoleSync(List.of(), context);
        }
        if (!context.groups().present()) {
            LOGGER.warn("role-sync.rules needs LuckPerms. Install LuckPerms, or remove the rules to hide this warning");
            return new RoleSync(List.of(), context);
        }
        RoleSync sync = new RoleSync(states(config.rules()), context);
        sync.rules.forEach(sync::inspectGroup);
        return sync;
    }

    public List<RoleSyncRule> activeRules() {
        return rules.stream().filter(RuleState::active).map(state -> state.rule).toList();
    }

    public void discordReady() {
        Map<Long, RoleInfo> roles = context.discord().roles();
        if (roles.isEmpty()) {
            return;
        }
        for (RuleState state : rules) {
            if (state.role != Check.REFUSED) {
                state.role = checkRole(state.rule, roles.get(RolePlan.role(state.rule)));
            }
        }
    }

    public void playerJoined(PlayerProfile player) {
        context.links().byMinecraftId(player.id()).ifPresent(link -> sync(link, null));
    }

    public void linkChanged(LinkChange change) {
        if (change instanceof LinkChange.Linked linked) {
            sync(linked.link(), null);
        } else {
            reconcile(change.link(), null, RolePlan::unlink);
        }
    }

    public void memberUpdated(MemberLookup.Member member) {
        context.links().byDiscordId(new DiscordId(member.userId())).ifPresent(link -> sync(link, member));
    }

    public void memberLeft(long userId) {
        context.links()
                .byDiscordId(new DiscordId(userId))
                .ifPresent(link -> sync(link, new MemberLookup.NotMember(userId)));
    }

    private void sync(Link link, @Nullable MemberLookup known) {
        reconcile(link, known, RolePlan::sync);
    }

    private void reconcile(Link link, @Nullable MemberLookup known, Planner planner) {
        if (activeRules().isEmpty()) {
            return;
        }
        long userId = link.discordId().value();
        MemberLookup recent = known != null
                ? known
                : context.memory().freshMember(userId, context.now()).orElse(null);
        CompletableFuture<MemberLookup> member = recent != null
                ? CompletableFuture.completedFuture(recent)
                : context.discord()
                        .member(userId)
                        .exceptionally(failure -> new MemberLookup.Unavailable(String.valueOf(failure.getMessage())));
        CompletableFuture<Optional<PlayerGroups>> groups = context.groups()
                .groupsOf(link.minecraftId())
                .thenApply(Optional::of)
                .exceptionally(failure -> {
                    LOGGER.warn(
                            "Could not read the LuckPerms groups of {}: {}. Role sync skips them until they rejoin",
                            link.minecraftName(),
                            failure.getMessage());
                    return Optional.empty();
                });
        var unused = member.thenAcceptBoth(
                groups,
                (lookup, found) -> context.loop().execute(() -> {
                    context.memory().remember(lookup, context.now());
                    apply(link, planner.plan(activeRules(), lookup, found.orElse(null)));
                }));
    }

    private void apply(Link link, List<RoleChange> changes) {
        for (RoleChange change : changes) {
            if (change instanceof RoleChange.AddGroup add) {
                report(context.groups().addGroup(link.minecraftId(), add.group()), link, "add to group " + add.group());
            } else if (change instanceof RoleChange.RemoveGroup remove) {
                report(
                        context.groups().removeGroup(link.minecraftId(), remove.group()),
                        link,
                        "remove from group " + remove.group());
            } else if (change instanceof RoleChange.AddRole add && assignable(add.roleId())) {
                long userId = link.discordId().value();
                report(context.discord().addRole(userId, add.roleId()), link, "give Discord role " + add.roleId());
            } else if (change instanceof RoleChange.RemoveRole remove && assignable(remove.roleId())) {
                long userId = link.discordId().value();
                report(
                        context.discord().removeRole(userId, remove.roleId()),
                        link,
                        "take Discord role " + remove.roleId());
            }
        }
    }

    private boolean assignable(long roleId) {
        RoleInfo info = context.discord().roles().get(roleId);
        Check verdict = info == null ? Check.PENDING : checkRole(ruleFor(roleId), info);
        if (verdict == Check.REFUSED) {
            rules.stream()
                    .filter(state -> RolePlan.role(state.rule) == roleId)
                    .forEach(state -> state.role = Check.REFUSED);
        }
        return verdict == Check.PASSED;
    }

    private RoleSyncRule ruleFor(long roleId) {
        return rules.stream()
                .map(state -> state.rule)
                .filter(rule -> rule.direction() == SyncDirection.MINECRAFT_TO_DISCORD && RolePlan.role(rule) == roleId)
                .findFirst()
                .orElseThrow();
    }

    private static void report(CompletableFuture<Void> change, Link link, String what) {
        var unused = change.whenComplete((done, failure) -> {
            if (failure == null) {
                LOGGER.info("Role sync: {} for {}", what, link.minecraftName());
            } else {
                LOGGER.warn(
                        "Role sync could not {} for {}: {}. For Discord roles, give the bot Manage Roles and move"
                                + " its role above the synced roles",
                        what,
                        link.minecraftName(),
                        failure.getMessage());
            }
        });
    }

    private void inspectGroup(RuleState state) {
        String group = RolePlan.group(state.rule);
        var unused = context.groups()
                .inspect(group)
                .whenComplete((check, failure) ->
                        context.loop().execute(() -> state.group = groupVerdict(state, check, failure)));
    }

    private static Check groupVerdict(RuleState state, @Nullable GroupCheck check, @Nullable Throwable failure) {
        String group = state.rule.group();
        if (failure != null || check == null) {
            LOGGER.warn(
                    "Could not read LuckPerms group {}: {}. Its role-sync rule stays off until /discord reload",
                    group,
                    failure == null ? "no answer" : failure.getMessage());
            return Check.REFUSED;
        }
        if (check instanceof GroupCheck.Missing) {
            LOGGER.warn(
                    "LuckPerms has no group named {}. Create it or fix role-sync.rules, then run /discord reload",
                    group);
            return Check.REFUSED;
        }
        if (check instanceof GroupCheck.Dangerous dangerous && state.grantsGroup()) {
            LOGGER.warn(
                    "Refused the role-sync rule for group {} because the group grants {}. A Discord role must not hand"
                            + " out operator power; sync a group without it",
                    group,
                    dangerous.node());
            return Check.REFUSED;
        }
        return Check.PASSED;
    }

    private static Check checkRole(RoleSyncRule rule, @Nullable RoleInfo role) {
        if (role == null) {
            LOGGER.warn(
                    "Discord role {} in role-sync.rules does not exist. Fix the role ID and run /discord reload",
                    rule.discordRole());
            return Check.REFUSED;
        }
        if (rule.direction() == SyncDirection.MINECRAFT_TO_DISCORD && role.dangerous()) {
            LOGGER.warn(
                    "Refused the role-sync rule for Discord role {} because it has {}. A Minecraft group must not hand"
                            + " out Discord moderator power; sync a role without these permissions",
                    role.name(),
                    String.join(", ", role.dangerousPermissions()));
            return Check.REFUSED;
        }
        if (rule.direction() == SyncDirection.MINECRAFT_TO_DISCORD && !role.assignable()) {
            LOGGER.warn(
                    "The bot cannot give Discord role {}. Give the bot Manage Roles and move its role above {}",
                    role.name(),
                    role.name());
            return Check.REFUSED;
        }
        return Check.PASSED;
    }

    private static List<RuleState> states(List<RoleSyncRule> rules) {
        List<RuleState> states = new ArrayList<>();
        for (RoleSyncRule rule : rules) {
            if (rule.direction() == SyncDirection.MINECRAFT_TO_DISCORD && hasReverse(rules, rule)) {
                LOGGER.warn(
                        "role-sync.rules syncs Discord role {} and group {} both ways; only discord-to-minecraft"
                                + " is used. Remove one of the two rules",
                        rule.discordRole(),
                        rule.group());
            } else {
                states.add(new RuleState(rule));
            }
        }
        return List.copyOf(states);
    }

    private static boolean hasReverse(List<RoleSyncRule> rules, RoleSyncRule rule) {
        return rules.stream()
                .anyMatch(other -> other.direction() == SyncDirection.DISCORD_TO_MINECRAFT
                        && RolePlan.role(other) == RolePlan.role(rule)
                        && RolePlan.group(other).equals(RolePlan.group(rule)));
    }
}
