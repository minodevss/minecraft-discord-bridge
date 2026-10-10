package com.minecraftdiscordbridge.core.access;

import static com.minecraftdiscordbridge.core.log.Log.LOGGER;

import com.minecraftdiscordbridge.core.config.LinkingConfig;
import com.minecraftdiscordbridge.core.config.UnreachablePolicy;
import com.minecraftdiscordbridge.core.discord.DiscordId;
import com.minecraftdiscordbridge.core.discord.MemberLookup;
import com.minecraftdiscordbridge.core.discord.RoleInfo;
import com.minecraftdiscordbridge.core.i18n.Replies;
import com.minecraftdiscordbridge.core.log.Failures;
import com.minecraftdiscordbridge.core.platform.JoinDecision;
import com.minecraftdiscordbridge.core.platform.Permission;
import com.minecraftdiscordbridge.core.platform.PlatformInfo;
import com.minecraftdiscordbridge.core.platform.PlayerProfile;
import com.minecraftdiscordbridge.core.storage.Edition;
import com.minecraftdiscordbridge.core.storage.Link;
import com.minecraftdiscordbridge.core.text.Format.Slot;
import com.minecraftdiscordbridge.core.text.TextRenderer;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class JoinGate {
    public static final Duration BUDGET = Duration.ofSeconds(3);
    private static final JoinDecision ALLOW = new JoinDecision.Allow();

    private final boolean active;
    private final UnreachablePolicy policy;
    private final Optional<DiscordId> requiredRole;
    private final AccessContext context;
    private final Replies replies;

    JoinGate(
            boolean active,
            UnreachablePolicy policy,
            Optional<DiscordId> requiredRole,
            AccessContext context,
            Replies replies) {
        this.active = active;
        this.policy = policy;
        this.requiredRole = requiredRole;
        this.context = context;
        this.replies = replies;
    }

    public static JoinGate create(LinkingConfig config, PlatformInfo info, AccessContext context, Replies replies) {
        boolean active = config.gatesJoins() && usable(config, info);
        return new JoinGate(active, config.whenDiscordUnreachable(), config.requiredRole(), context, replies);
    }

    public boolean active() {
        return active;
    }

    public void decide(PlayerProfile player, boolean bypass, CompletableFuture<JoinDecision> result) {
        if (!active) {
            result.complete(ALLOW);
        } else if (bypass) {
            bypassed(player, result);
        } else {
            var unused = context.groups()
                    .hasPermission(player.id(), Permission.GATE_BYPASS.node())
                    .exceptionally(failure -> false)
                    .thenAccept(granted -> context.loop().execute(() -> {
                        if (Boolean.TRUE.equals(granted)) {
                            bypassed(player, result);
                        } else {
                            checkLink(player, result);
                        }
                    }));
        }
    }

    public JoinDecision fallback(PlayerProfile player, boolean bypass) {
        if (!active || bypass) {
            return ALLOW;
        }
        LOGGER.warn(
                "Could not finish checking {} within {} seconds, so linking.when-discord-unreachable decides",
                player.name(),
                BUDGET.toSeconds());
        Optional<Link> link = context.links().byMinecraftId(player.id());
        if (link.isEmpty()) {
            return deny("game.gate.unreachable");
        }
        return requiredRole.isEmpty() ? ALLOW : unreachable(player.id());
    }

    private void checkLink(PlayerProfile player, CompletableFuture<JoinDecision> result) {
        Optional<Link> link = context.links().byMinecraftId(player.id());
        if (link.isEmpty()) {
            Edition edition = player.bedrock() ? Edition.BEDROCK : Edition.JAVA;
            String code = context.pending().issue(player.id(), player.name(), edition, context.now());
            LOGGER.info(
                    "Kept {} out because their account is not linked; the kick screen shows a link code",
                    player.name());
            result.complete(deny("game.gate.link", Slot.text("code", code)));
            return;
        }
        if (requiredRole.isEmpty()) {
            allow(player, result);
            return;
        }
        DiscordId userId = link.get().discordId();
        Optional<MemberLookup> cached = context.memory().freshMember(userId, context.now());
        if (cached.isPresent()) {
            judge(player, cached.get(), result);
            return;
        }
        var unused = context.discord()
                .member(userId)
                .exceptionally(failure -> new MemberLookup.Unavailable(Failures.describe(failure)))
                .thenAccept(lookup -> context.loop().execute(() -> {
                    context.memory().remember(lookup, context.now());
                    judge(player, lookup, result);
                }));
    }

    private void judge(PlayerProfile player, MemberLookup lookup, CompletableFuture<JoinDecision> result) {
        DiscordId role = requiredRole.orElseThrow();
        if (lookup instanceof MemberLookup.Member member && member.roleIds().contains(role)) {
            allow(player, result);
        } else if (lookup instanceof MemberLookup.Member) {
            LOGGER.info("Kept {} out because their Discord account lacks the required role", player.name());
            result.complete(deny("game.gate.role", Slot.text("role", roleName(role))));
        } else if (lookup instanceof MemberLookup.NotMember) {
            LOGGER.info("Kept {} out because their Discord account is not in the Discord server", player.name());
            result.complete(deny("game.gate.not-member"));
        } else {
            LOGGER.warn(
                    "Could not check the Discord roles of {}: {}. linking.when-discord-unreachable decides",
                    player.name(),
                    ((MemberLookup.Unavailable) lookup).reason());
            result.complete(unreachable(player.id()));
        }
    }

    private JoinDecision unreachable(UUID player) {
        if (policy == UnreachablePolicy.ALLOW || context.memory().allowedRecently(player, context.now())) {
            return ALLOW;
        }
        return deny("game.gate.unreachable");
    }

    private void allow(PlayerProfile player, CompletableFuture<JoinDecision> result) {
        context.memory().allowed(player.id(), context.now());
        result.complete(ALLOW);
    }

    private void bypassed(PlayerProfile player, CompletableFuture<JoinDecision> result) {
        LOGGER.info(
                "Let {} join without the Discord check because they have {}",
                player.name(),
                Permission.GATE_BYPASS.node());
        result.complete(ALLOW);
    }

    private String roleName(DiscordId role) {
        RoleInfo info = context.discord().roles().get(role);
        return info == null ? role.toString() : info.name();
    }

    private JoinDecision deny(String key, Slot... slots) {
        return new JoinDecision.Deny(TextRenderer.toJson(replies.lang().component(key, slots), replies.dataVersion()));
    }

    private static boolean usable(LinkingConfig config, PlatformInfo info) {
        if (!config.enabled()) {
            LOGGER.warn("The join gate stays off because linking.enabled is false, so nobody could link; set it to"
                    + " true, or clear linking.require-for-join and linking.required-role");
            return false;
        }
        if (!info.verifiesPlayers()) {
            LOGGER.warn("The join gate stays off because {}", Offline.WHY);
            return false;
        }
        return true;
    }
}
