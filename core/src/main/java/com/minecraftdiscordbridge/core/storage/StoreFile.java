package com.minecraftdiscordbridge.core.storage;

import static com.minecraftdiscordbridge.core.log.Log.LOGGER;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.core.util.Separators;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.DateTimeException;
import java.util.function.Function;

final class StoreFile {
    static final int FORMAT = 1;

    private static final ObjectMapper JSON = JsonMapper.builder()
            .enable(
                    DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES,
                    DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES,
                    DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .defaultSetterInfo(JsonSetter.Value.construct(Nulls.FAIL, Nulls.FAIL))
            .enable(SerializationFeature.INDENT_OUTPUT)
            .defaultPrettyPrinter(new DefaultPrettyPrinter(
                    Separators.createDefaultInstance().withObjectFieldValueSpacing(Separators.Spacing.AFTER)))
            .build();

    private final Path path;
    private final String contents;
    private final int backups;
    private final boolean secret;
    private volatile boolean readOnly;

    private StoreFile(Path path, String contents, int backups, boolean secret) {
        this.path = path;
        this.contents = contents;
        this.backups = backups;
        this.secret = secret;
    }

    static StoreFile withBackups(Path dataDirectory, String fileName, String contents) {
        return new StoreFile(locate(dataDirectory, fileName), contents, 3, false);
    }

    static StoreFile plain(Path dataDirectory, String fileName, String contents) {
        return new StoreFile(locate(dataDirectory, fileName), contents, 0, false);
    }

    static StoreFile ownerOnly(Path dataDirectory, String fileName, String contents) {
        return new StoreFile(locate(dataDirectory, fileName), contents, 0, true);
    }

    static StoreFile cache(Path dataDirectory, String fileName, String contents) {
        return new StoreFile(locate(dataDirectory, "cache").resolve(fileName), contents, 0, false);
    }

    private static Path locate(Path dataDirectory, String fileName) {
        return dataDirectory.resolve("data").resolve(fileName);
    }

    Path path() {
        return path;
    }

    boolean readOnly() {
        return readOnly;
    }

    <D extends Versioned, T> T load(Class<D> type, Function<D, T> decode, T empty) {
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(path);
        } catch (NoSuchFileException missing) {
            return empty;
        } catch (IOException unreadable) {
            return damaged("it cannot be read: " + oneLine(String.valueOf(unreadable.getMessage())), empty);
        }
        try {
            D document = JSON.readValue(bytes, type);
            if (document.version() != FORMAT) {
                return damaged(
                        "it uses format " + document.version() + ", which this version of the bridge does not know",
                        empty);
            }
            return decode.apply(document);
        } catch (JsonProcessingException invalid) {
            return damaged(describe(invalid), empty);
        } catch (IOException unreadable) {
            return damaged("it cannot be read: " + oneLine(String.valueOf(unreadable.getMessage())), empty);
        } catch (IllegalArgumentException | DateTimeException wrongValue) {
            return damaged(
                    secret ? "it has an invalid value" : oneLine(String.valueOf(wrongValue.getMessage())), empty);
        }
    }

    void save(Versioned document) throws IOException {
        if (readOnly) {
            throw new ReadOnlyStoreException(contents + " were not saved because " + path
                    + " could not be read at startup. Fix or delete that file, then restart the server.");
        }
        byte[] bytes = JSON.writeValueAsBytes(document);
        keepBackup();
        if (secret) {
            AtomicFile.writeOwnerOnly(path, bytes);
        } else {
            AtomicFile.write(path, bytes);
        }
    }

    private void keepBackup() throws IOException {
        if (backups == 0 || !Files.exists(path)) {
            return;
        }
        for (int index = backups - 1; index >= 1; index--) {
            Path older = backup(index);
            if (Files.exists(older)) {
                Files.move(older, backup(index + 1), StandardCopyOption.REPLACE_EXISTING);
            }
        }
        AtomicFile.write(backup(1), Files.readAllBytes(path));
    }

    private Path backup(int index) {
        return path.resolveSibling("backups").resolve(path.getFileName() + "." + index + ".bak");
    }

    private <T> T damaged(String reason, T empty) {
        readOnly = true;
        LOGGER.error(
                "{} is damaged: {}. {} are read-only until you fix or delete the file and restart the server.",
                path,
                reason,
                contents);
        return empty;
    }

    private String describe(JsonProcessingException invalid) {
        JsonLocation location = invalid.getLocation();
        String where =
                location == null ? "" : " at line " + location.getLineNr() + ", column " + location.getColumnNr();
        if (secret) {
            return "it is not valid" + where;
        }
        return oneLine(String.valueOf(invalid.getOriginalMessage())) + where;
    }

    private static String oneLine(String text) {
        return text.replaceAll("\\R+", " ").strip();
    }
}
