package com.minecraftdiscordbridge.core.discord.jda;

sealed interface PreflightResult {
    record Ready(ApplicationInfo application) implements PreflightResult {}

    record InvalidToken() implements PreflightResult {}

    record Unreachable(String reason) implements PreflightResult {}
}
