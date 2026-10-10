package com.minecraftdiscordbridge.core;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.ILoggerFactory;
import org.slf4j.IMarkerFactory;
import org.slf4j.Marker;
import org.slf4j.event.Level;
import org.slf4j.helpers.BasicMarkerFactory;
import org.slf4j.helpers.LegacyAbstractLogger;
import org.slf4j.helpers.MessageFormatter;
import org.slf4j.helpers.NOPMDCAdapter;
import org.slf4j.spi.MDCAdapter;
import org.slf4j.spi.SLF4JServiceProvider;

public final class RecordingLogs implements SLF4JServiceProvider {
    private static final String BRIDGE = "MinecraftDiscordBridge";
    private static final List<Capture> CAPTURES = new CopyOnWriteArrayList<>();

    private final IMarkerFactory markers = new BasicMarkerFactory();
    private final MDCAdapter mdc = new NOPMDCAdapter();

    public static Capture capture() {
        Capture capture = new Capture();
        CAPTURES.add(capture);
        return capture;
    }

    @Override
    public ILoggerFactory getLoggerFactory() {
        return Recorder::new;
    }

    @Override
    public IMarkerFactory getMarkerFactory() {
        return markers;
    }

    @Override
    public MDCAdapter getMDCAdapter() {
        return mdc;
    }

    @Override
    public String getRequestedApiVersion() {
        return "2.0.99";
    }

    @Override
    public void initialize() {}

    public static final class Capture implements AutoCloseable {
        private final List<String> lines = new CopyOnWriteArrayList<>();

        public List<String> lines() {
            return List.copyOf(lines);
        }

        public List<String> lines(Level level) {
            String prefix = level + " ";
            return lines.stream()
                    .filter(line -> line.startsWith(prefix))
                    .map(line -> line.substring(prefix.length()))
                    .toList();
        }

        @Override
        public void close() {
            CAPTURES.remove(this);
        }
    }

    private static final class Recorder extends LegacyAbstractLogger {
        private static final long serialVersionUID = 1L;

        Recorder(String name) {
            this.name = name;
        }

        @Override
        public boolean isTraceEnabled() {
            return false;
        }

        @Override
        public boolean isDebugEnabled() {
            return false;
        }

        @Override
        public boolean isInfoEnabled() {
            return BRIDGE.equals(name);
        }

        @Override
        public boolean isWarnEnabled() {
            return BRIDGE.equals(name);
        }

        @Override
        public boolean isErrorEnabled() {
            return BRIDGE.equals(name);
        }

        @Override
        protected String getFullyQualifiedCallerName() {
            return Recorder.class.getName();
        }

        @Override
        protected void handleNormalizedLoggingCall(
                Level level, Marker marker, String pattern, Object[] arguments, Throwable thrown) {
            String line = level + " " + MessageFormatter.basicArrayFormat(pattern, arguments);
            CAPTURES.forEach(capture -> capture.lines.add(line));
        }
    }
}
