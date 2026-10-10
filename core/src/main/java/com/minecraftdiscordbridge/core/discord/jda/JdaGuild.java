package com.minecraftdiscordbridge.core.discord.jda;

import com.minecraftdiscordbridge.core.discord.DiscordId;
import com.minecraftdiscordbridge.core.discord.MemberLookup;
import com.minecraftdiscordbridge.core.discord.RoleInfo;
import com.minecraftdiscordbridge.core.log.Failures;
import java.time.Duration;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import org.jspecify.annotations.Nullable;

final class JdaGuild {
    private static final String NOT_CONNECTED = "Discord is not connected";
    private static final Duration MEMBER_LOOKUP_TIMEOUT = Duration.ofSeconds(5);
    private static final String ROLE_SYNC_REASON = "Minecraft Discord Bridge role sync";
    private static final Map<Permission, String> DANGEROUS_PERMISSIONS = dangerousPermissions();

    private final Supplier<@Nullable Guild> guild;

    JdaGuild(Supplier<@Nullable Guild> guild) {
        this.guild = guild;
    }

    static MemberLookup.Member member(Member member) {
        Set<DiscordId> roles = member.getRoles().stream()
                .map(role -> new DiscordId(role.getIdLong()))
                .collect(Collectors.toUnmodifiableSet());
        return new MemberLookup.Member(new DiscordId(member.getIdLong()), roles);
    }

    CompletableFuture<MemberLookup> member(DiscordId userId) {
        Guild current = guild.get();
        if (current == null) {
            return CompletableFuture.completedFuture(new MemberLookup.Unavailable(NOT_CONNECTED));
        }
        try {
            return current.retrieveMemberById(userId.value())
                    .useCache(false)
                    .timeout(MEMBER_LOOKUP_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
                    .submit()
                    .handle((member, failure) -> failure == null ? member(member) : lookupFailed(userId, failure));
        } catch (RuntimeException failure) {
            return CompletableFuture.completedFuture(new MemberLookup.Unavailable(Failures.describe(failure)));
        }
    }

    Map<DiscordId, RoleInfo> roles() {
        Guild current = guild.get();
        if (current == null) {
            return Map.of();
        }
        Map<DiscordId, RoleInfo> roles = new HashMap<>();
        for (Role role : current.getRoles()) {
            boolean assignable = !role.isManaged()
                    && !role.isPublicRole()
                    && current.getSelfMember().canInteract(role);
            DiscordId id = new DiscordId(role.getIdLong());
            roles.put(id, new RoleInfo(id, role.getName(), dangerous(role.getPermissions()), assignable));
        }
        return Map.copyOf(roles);
    }

    CompletableFuture<Void> changeRole(DiscordId userId, DiscordId roleId, boolean add) {
        Guild current = guild.get();
        if (current == null) {
            return CompletableFuture.failedFuture(new IllegalStateException(NOT_CONNECTED));
        }
        Role role = current.getRoleById(roleId.value());
        if (role == null) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Discord role " + roleId + " does not exist"));
        }
        try {
            UserSnowflake user = UserSnowflake.fromId(userId.value());
            return (add ? current.addRoleToMember(user, role) : current.removeRoleFromMember(user, role))
                    .reason(ROLE_SYNC_REASON)
                    .submit();
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    private static MemberLookup lookupFailed(DiscordId userId, Throwable failure) {
        Throwable cause = Failures.unwrap(failure);
        if (cause instanceof ErrorResponseException error
                && (error.getErrorResponse() == ErrorResponse.UNKNOWN_MEMBER
                        || error.getErrorResponse() == ErrorResponse.UNKNOWN_USER)) {
            return new MemberLookup.NotMember(userId);
        }
        return new MemberLookup.Unavailable(Failures.describe(failure));
    }

    private static Set<String> dangerous(Set<Permission> granted) {
        Set<String> found = new LinkedHashSet<>();
        DANGEROUS_PERMISSIONS.forEach((permission, name) -> {
            if (granted.contains(permission)) {
                found.add(name);
            }
        });
        return found;
    }

    private static Map<Permission, String> dangerousPermissions() {
        Map<Permission, String> names = new EnumMap<>(Permission.class);
        names.put(Permission.ADMINISTRATOR, "Administrator");
        names.put(Permission.MANAGE_SERVER, "Manage Server");
        names.put(Permission.MANAGE_ROLES, "Manage Roles");
        names.put(Permission.BAN_MEMBERS, "Ban Members");
        names.put(Permission.KICK_MEMBERS, "Kick Members");
        names.put(Permission.MESSAGE_MENTION_EVERYONE, "Mention @everyone");
        return names;
    }
}
