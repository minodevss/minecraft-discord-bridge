package com.minecraftdiscordbridge.core;

import com.minecraftdiscordbridge.core.linking.Attempts;
import com.minecraftdiscordbridge.core.linking.PendingLinks;
import com.minecraftdiscordbridge.core.loop.CoreLoop;
import com.minecraftdiscordbridge.core.loop.IoExecutor;
import com.minecraftdiscordbridge.core.platform.Platform;
import com.minecraftdiscordbridge.core.setup.SetupCodes;
import com.minecraftdiscordbridge.core.status.Roster;
import com.minecraftdiscordbridge.core.vanilla.VanillaTranslations;
import java.time.Duration;
import java.time.InstantSource;

record Shared(
        Platform platform,
        CoreLoop loop,
        IoExecutor io,
        InstantSource clock,
        DiscordHandle discord,
        Roster roster,
        PendingLinks pendingLinks,
        SetupCodes setupCodes,
        Attempts linkAttempts,
        Attempts setupAttempts,
        Duration actionTimeout,
        VanillaTranslations vanilla) {}
