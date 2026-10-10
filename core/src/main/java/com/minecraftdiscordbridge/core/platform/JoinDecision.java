package com.minecraftdiscordbridge.core.platform;

public sealed interface JoinDecision {
    record Allow() implements JoinDecision {}

    record Deny(String json) implements JoinDecision {}
}
