package com.minecraftdiscordbridge.core.access;

sealed interface RoleChange {
    record AddGroup(String group) implements RoleChange {}

    record RemoveGroup(String group) implements RoleChange {}

    record AddRole(long roleId) implements RoleChange {}

    record RemoveRole(long roleId) implements RoleChange {}
}
