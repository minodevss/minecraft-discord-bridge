package com.minecraftdiscordbridge.core.console;

import org.jspecify.annotations.Nullable;

record ConsoleLine(
        long timeMillis,
        String level,
        String message,
        @Nullable String thrown) {}
