package com.minecraftdiscordbridge.core.access;

import com.minecraftdiscordbridge.core.discord.DiscordId;

sealed interface RoleChange {
    record AddGroup(String group) implements RoleChange {}

    record RemoveGroup(String group) implements RoleChange {}

    record AddRole(DiscordId roleId) implements RoleChange {}

    record RemoveRole(DiscordId roleId) implements RoleChange {}
}
