package com.minecraftdiscordbridge.core.feature;

import com.minecraftdiscordbridge.core.discord.Discord;
import com.minecraftdiscordbridge.core.i18n.Replies;
import com.minecraftdiscordbridge.core.loop.IoExecutor;
import java.time.InstantSource;

public record FeatureContext(Discord discord, IoExecutor io, InstantSource clock, Replies replies) {}
