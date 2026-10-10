package com.minecraftdiscordbridge.core.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AtomicFileTest {
    @TempDir
    Path directory;

    @Test
    void writesANewFileAndItsMissingDirectories() throws IOException {
        Path target = directory.resolve("data").resolve("links.json");

        AtomicFile.write(target, bytes("hello"));

        assertThat(target).hasContent("hello");
    }

    @Test
    void replacesTheOldContentAndLeavesNoTempFiles() throws IOException {
        Path target = directory.resolve("config.yml");
        Files.writeString(target, "old");

        AtomicFile.write(target, bytes("new"));

        assertThat(target).hasContent("new");
        assertThat(fileNames()).containsExactly("config.yml");
    }

    @Test
    void createsOwnerOnlyFiles() throws IOException {
        assumePosix();
        Path target = directory.resolve("webhooks.json");

        AtomicFile.writeOwnerOnly(target, bytes("{}"));

        assertThat(permissions(target)).isEqualTo("rw-------");
    }

    @Test
    void keepsThePermissionsOfTheFileItReplaces() throws IOException {
        assumePosix();
        Path target = directory.resolve("config.yml");
        Files.writeString(target, "old");
        Files.setPosixFilePermissions(target, PosixFilePermissions.fromString("rw-rw----"));

        AtomicFile.write(target, bytes("new"));

        assertThat(permissions(target)).isEqualTo("rw-rw----");
    }

    @Test
    void retriesWhileWindowsKeepsTheFileLocked() throws IOException {
        Path target = directory.resolve("config.yml");
        Files.writeString(target, "old");
        AtomicInteger attempts = new AtomicInteger();
        List<Long> sleeps = new ArrayList<>();
        AtomicFile windows = new AtomicFile(
                (source, destination) -> {
                    if (attempts.incrementAndGet() <= 2) {
                        throw new AccessDeniedException(destination.toString());
                    }
                    Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
                },
                sleeps::add,
                true);

        windows.replace(target, bytes("new"), false);

        assertThat(target).hasContent("new");
        assertThat(sleeps).containsExactly(25L, 50L);
    }

    @Test
    void givesUpWithinFiveSecondsAndKeepsTheOldFile() throws IOException {
        Path target = directory.resolve("config.yml");
        Files.writeString(target, "old");
        List<Long> sleeps = new ArrayList<>();
        AtomicFile windows = new AtomicFile(
                (source, destination) -> {
                    throw new AccessDeniedException(destination.toString());
                },
                sleeps::add,
                true);

        assertThatThrownBy(() -> windows.replace(target, bytes("new"), false))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("another program keeps it open")
                .hasMessageContaining("the old file is unchanged");
        assertThat(sleeps.stream().mapToLong(Long::longValue).sum())
                .isPositive()
                .isLessThanOrEqualTo(5000);
        assertThat(target).hasContent("old");
        assertThat(fileNames()).containsExactly("config.yml");
    }

    @Test
    void doesNotRetryAccessDeniedOutsideWindows() throws IOException {
        Path target = directory.resolve("config.yml");
        List<Long> sleeps = new ArrayList<>();
        AtomicFile unix = new AtomicFile(
                (source, destination) -> {
                    throw new AccessDeniedException(destination.toString());
                },
                sleeps::add,
                false);

        assertThatThrownBy(() -> unix.replace(target, bytes("new"), false)).isInstanceOf(AccessDeniedException.class);
        assertThat(sleeps).isEmpty();
    }

    @Test
    void tellsDockerUsersToMountTheDirectoryWhenTheFileIsBusy() throws IOException {
        Path target = directory.resolve("config.yml");
        Files.writeString(target, "old");
        AtomicFile bindMounted = new AtomicFile(
                (source, destination) -> {
                    throw new FileSystemException(source.toString(), destination.toString(), "Device or resource busy");
                },
                millis -> {},
                false);

        assertThatThrownBy(() -> bindMounted.replace(target, bytes("new"), false))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Device or resource busy")
                .hasMessageContaining("mount the directory that contains it instead");
        assertThat(target).hasContent("old");
        assertThat(fileNames()).containsExactly("config.yml");
    }

    private List<String> fileNames() throws IOException {
        try (var files = Files.list(directory)) {
            return files.map(path -> path.getFileName().toString()).toList();
        }
    }

    private static String permissions(Path file) throws IOException {
        return PosixFilePermissions.toString(Files.getPosixFilePermissions(file));
    }

    private static void assumePosix() {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
