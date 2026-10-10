package com.minecraftdiscordbridge.core.console;

import com.minecraftdiscordbridge.core.log.Log;
import java.util.List;
import java.util.Optional;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.message.Message;
import org.jspecify.annotations.Nullable;

final class ConsoleAppender extends AbstractAppender {
    static final String NAME = "MinecraftDiscordBridgeConsole";
    private static final List<String> IGNORED_PREFIXES =
            List.of("net.dv8tion.", "okhttp3.", "com.minecraftdiscordbridge.");

    private final ConsoleBuffer buffer;

    ConsoleAppender(ConsoleBuffer buffer) {
        super(NAME, null, null, true, Property.EMPTY_ARRAY);
        this.buffer = buffer;
    }

    @Override
    public void append(LogEvent event) {
        if (ignored(event.getLoggerName())) {
            return;
        }
        Message message = event.getMessage();
        Throwable thrown = event.getThrown();
        buffer.offer(new ConsoleLine(
                event.getTimeMillis(),
                event.getLevel().name(),
                message == null ? "" : message.getFormattedMessage(),
                Optional.ofNullable(thrown).map(Throwable::toString)));
    }

    static boolean ignored(@Nullable String logger) {
        if (logger == null || logger.equals(Log.NAME)) {
            return true;
        }
        for (String prefix : IGNORED_PREFIXES) {
            if (logger.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
