package com.minecraftdiscordbridge.core.console;

import java.util.Optional;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.LoggerConfig;

final class Log4jHook {
    private Log4jHook() {}

    static Optional<Runnable> attach(ConsoleBuffer buffer) {
        try {
            if (!(LogManager.getContext(false) instanceof LoggerContext context)) {
                return Optional.empty();
            }
            ConsoleAppender appender = new ConsoleAppender(buffer);
            appender.start();
            LoggerConfig root = context.getConfiguration().getRootLogger();
            root.addAppender(appender, Level.INFO, null);
            context.updateLoggers();
            return Optional.of(() -> {
                root.removeAppender(ConsoleAppender.NAME);
                context.updateLoggers();
                appender.stop();
            });
        } catch (LinkageError | RuntimeException unavailable) {
            return Optional.empty();
        }
    }
}
