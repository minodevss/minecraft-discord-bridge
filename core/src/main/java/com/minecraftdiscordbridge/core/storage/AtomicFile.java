package com.minecraftdiscordbridge.core.storage;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AtomicFile {
    private static final Logger LOGGER = LoggerFactory.getLogger("MinecraftDiscordBridge");
    private static final Duration RETRY_BUDGET = Duration.ofSeconds(5);
    private static final Duration FIRST_RETRY = Duration.ofMillis(25);
    private static final Duration LONGEST_RETRY = Duration.ofSeconds(1);
    private static final Set<PosixFilePermission> OWNER_ONLY =
            Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
    private static final Set<OpenOption> CREATE_NEW = Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    private static final AtomicFile SYSTEM = new AtomicFile(AtomicFile::atomicMove, Thread::sleep, runningOnWindows());

    private final Mover mover;
    private final Sleeper sleeper;
    private final boolean windows;

    AtomicFile(Mover mover, Sleeper sleeper, boolean windows) {
        this.mover = mover;
        this.sleeper = sleeper;
        this.windows = windows;
    }

    public static void write(Path target, byte[] content) throws IOException {
        SYSTEM.replace(target, content, false);
    }

    public static void writeOwnerOnly(Path target, byte[] content) throws IOException {
        SYSTEM.replace(target, content, true);
    }

    void replace(Path target, byte[] content, boolean ownerOnly) throws IOException {
        Path absolute = target.toAbsolutePath();
        Path directory = absolute.getParent();
        if (directory == null) {
            throw new IOException("Cannot write " + target + " because it has no parent directory");
        }
        Files.createDirectories(directory);
        Path temp = directory.resolve("." + absolute.getFileName() + "." + randomSuffix() + ".tmp");
        try {
            writeSynced(temp, content, ownerOnly, absolute);
            moveIntoPlace(temp, absolute);
        } catch (IOException | RuntimeException failure) {
            deleteQuietly(temp, failure);
            throw failure;
        }
        syncDirectory(directory);
    }

    private static void writeSynced(Path temp, byte[] content, boolean ownerOnly, Path target) throws IOException {
        boolean posix = supportsPosix(temp);
        FileAttribute<?>[] attributes = ownerOnly && posix
                ? new FileAttribute<?>[] {PosixFilePermissions.asFileAttribute(OWNER_ONLY)}
                : new FileAttribute<?>[0];
        try (FileChannel channel = FileChannel.open(temp, CREATE_NEW, attributes)) {
            if (!ownerOnly && posix && Files.exists(target)) {
                Files.setPosixFilePermissions(temp, Files.getPosixFilePermissions(target));
            }
            ByteBuffer buffer = ByteBuffer.wrap(content);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
        }
    }

    private void moveIntoPlace(Path temp, Path target) throws IOException {
        Duration waited = Duration.ZERO;
        Duration delay = FIRST_RETRY;
        while (true) {
            try {
                mover.move(temp, target);
                return;
            } catch (AccessDeniedException denied) {
                if (!windows) {
                    throw denied;
                }
                if (waited.plus(delay).compareTo(RETRY_BUDGET) > 0) {
                    throw new IOException(
                            "Could not replace " + target
                                    + " because another program keeps it open (often antivirus, backup or an editor);"
                                    + " the old file is unchanged. Close that program and try again.",
                            denied);
                }
                pause(delay);
                waited = waited.plus(delay);
                delay = min(delay.multipliedBy(2), LONGEST_RETRY);
            } catch (FileSystemException failed) {
                if (isBusy(failed)) {
                    throw new IOException(
                            "Could not replace " + target + " (" + failed.getReason() + "). It is probably mounted"
                                    + " into the container as a single file; mount the directory that contains it"
                                    + " instead.",
                            failed);
                }
                throw failed;
            }
        }
    }

    private void pause(Duration delay) throws InterruptedIOException {
        try {
            sleeper.sleep(delay.toMillis());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Interrupted while waiting to replace a file");
        }
    }

    private static boolean isBusy(FileSystemException failed) {
        String reason = failed.getReason();
        return reason != null && reason.toLowerCase(Locale.ROOT).contains("resource busy");
    }

    private void syncDirectory(Path directory) {
        if (windows) {
            return;
        }
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (IOException unsupported) {
            LOGGER.debug("Could not sync directory {}", directory, unsupported);
        }
    }

    private static void deleteQuietly(Path temp, Exception failure) {
        try {
            Files.deleteIfExists(temp);
        } catch (IOException cleanup) {
            failure.addSuppressed(cleanup);
        }
    }

    private static boolean supportsPosix(Path path) {
        return path.getFileSystem().supportedFileAttributeViews().contains("posix");
    }

    private static Duration min(Duration a, Duration b) {
        return a.compareTo(b) <= 0 ? a : b;
    }

    private static String randomSuffix() {
        return Long.toHexString(ThreadLocalRandom.current().nextLong());
    }

    private static void atomicMove(Path source, Path target) throws IOException {
        Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    private static boolean runningOnWindows() {
        return System.getProperty("os.name", "").startsWith("Windows");
    }

    @FunctionalInterface
    interface Mover {
        void move(Path source, Path target) throws IOException;
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }
}
