package com.minecraftdiscordbridge.core.console;

import java.util.Optional;

record ConsoleLine(long timeMillis, String level, String message, Optional<String> thrown) {}
