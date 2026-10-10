package com.minecraftdiscordbridge.core.access;

import static com.minecraftdiscordbridge.core.log.Log.LOGGER;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedPermissionData;
import net.luckperms.api.event.EventBus;
import net.luckperms.api.event.EventSubscription;
import net.luckperms.api.event.node.NodeMutateEvent;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import net.luckperms.api.event.user.UserUnloadEvent;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.query.QueryOptions;

final class LuckPermsGroups implements PermissionGroups {
    private LuckPermsGroups() {}

    static PermissionGroups create() {
        return new LuckPermsGroups();
    }

    @Override
    public boolean present() {
        return true;
    }

    @Override
    public CompletableFuture<GroupCheck> inspect(String group) {
        return call(api -> api.getGroupManager()
                .loadGroup(group)
                .thenApply(found -> found.map(LuckPermsGroups::check).orElseGet(GroupCheck.Missing::new)));
    }

    @Override
    public CompletableFuture<PlayerGroups> groupsOf(UUID player) {
        return call(api -> api.getUserManager().loadUser(player).thenApply(LuckPermsGroups::groups));
    }

    @Override
    public CompletableFuture<Void> addGroup(UUID player, String group) {
        return call(api ->
                api.getUserManager().modifyUser(player, user -> user.data().add(membership(group))));
    }

    @Override
    public CompletableFuture<Void> removeGroup(UUID player, String group) {
        return call(api ->
                api.getUserManager().modifyUser(player, user -> user.data().remove(membership(group))));
    }

    @Override
    public CompletableFuture<Boolean> hasPermission(UUID player, String node) {
        return call(api -> api.getUserManager()
                .loadUser(player)
                .thenApply(user -> user.getCachedData()
                        .getPermissionData(user.getQueryOptions())
                        .checkPermission(node)
                        .asBoolean()));
    }

    @Override
    public Runnable onGroupsChanged(Consumer<UUID> changed) {
        try {
            EventBus events = LuckPermsProvider.get().getEventBus();
            GroupChanges changes = new GroupChanges(changed);
            List<EventSubscription<?>> subscriptions = List.of(
                    events.subscribe(UserDataRecalculateEvent.class, event -> {
                        User user = event.getUser();
                        changes.recalculated(user.getUniqueId(), groups(user).inherited());
                    }),
                    events.subscribe(NodeMutateEvent.class, event -> {
                        if (event.getTarget() instanceof User user && changesInheritance(event)) {
                            changes.edited(user.getUniqueId());
                        }
                    }),
                    events.subscribe(
                            UserUnloadEvent.class,
                            event -> changes.unloaded(event.getUser().getUniqueId())));
            return () -> subscriptions.forEach(EventSubscription::close);
        } catch (RuntimeException | LinkageError unavailable) {
            LOGGER.warn(
                    "Could not watch LuckPerms for group changes ({}). Role sync updates Discord roles when players"
                            + " join or link, or after /discord reload",
                    unavailable.toString());
            return () -> {};
        }
    }

    private static <T> CompletableFuture<T> call(Function<LuckPerms, CompletableFuture<T>> request) {
        try {
            return request.apply(LuckPermsProvider.get());
        } catch (RuntimeException | LinkageError unavailable) {
            return CompletableFuture.failedFuture(unavailable);
        }
    }

    private static GroupCheck check(Group group) {
        CachedPermissionData permissions = group.getCachedData().getPermissionData(QueryOptions.nonContextual());
        return GroupDanger.find(
                        permissions.getPermissionMap(),
                        node -> permissions.checkPermission(node).asBoolean())
                .<GroupCheck>map(GroupCheck.Dangerous::new)
                .orElseGet(GroupCheck.Safe::new);
    }

    private static PlayerGroups groups(User user) {
        Set<String> direct = user.getNodes(NodeType.INHERITANCE).stream()
                .filter(node -> node.getContexts().isEmpty() && !node.hasExpiry())
                .map(InheritanceNode::getGroupName)
                .collect(Collectors.toSet());
        Set<String> inherited = user.getInheritedGroups(user.getQueryOptions()).stream()
                .map(Group::getName)
                .collect(Collectors.toSet());
        return new PlayerGroups(direct, inherited);
    }

    private static boolean changesInheritance(NodeMutateEvent event) {
        return !inheritance(event.getDataBefore()).equals(inheritance(event.getDataAfter()));
    }

    private static Set<Node> inheritance(Set<Node> nodes) {
        return nodes.stream().filter(NodeType.INHERITANCE::matches).collect(Collectors.toSet());
    }

    private static InheritanceNode membership(String group) {
        return InheritanceNode.builder(group).build();
    }
}
