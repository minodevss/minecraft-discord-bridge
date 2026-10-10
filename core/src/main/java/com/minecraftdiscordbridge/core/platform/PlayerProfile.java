package com.minecraftdiscordbridge.core.platform;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record PlayerProfile(
        UUID id, String name, String displayName, @Nullable String skinTexture, boolean bedrock) {}
