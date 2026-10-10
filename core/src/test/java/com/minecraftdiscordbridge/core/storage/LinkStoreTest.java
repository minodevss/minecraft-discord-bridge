package com.minecraftdiscordbridge.core.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.minecraftdiscordbridge.core.discord.DiscordId;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LinkStoreTest {
    private static final Instant NOW = Instant.parse("2026-10-10T12:00:00Z");
    private static final Link STEVE = new Link(
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            new DiscordId(100000000000000001L),
            "Steve",
            Edition.JAVA,
            NOW);
    private static final Link ALEX = new Link(
            UUID.fromString("00000000-0000-0000-0000-000000000002"),
            new DiscordId(100000000000000002L),
            ".Alex",
            Edition.BEDROCK,
            NOW);

    @TempDir
    Path directory;

    @Test
    void findsALinkFromEitherAccount() throws IOException {
        LinkStore store = LinkStore.open(directory);

        store.link(STEVE);

        assertThat(store.byMinecraftId(STEVE.minecraftId())).contains(STEVE);
        assertThat(store.byDiscordId(STEVE.discordId())).contains(STEVE);
        assertThat(store.byDiscordId(ALEX.discordId())).isEmpty();
    }

    @Test
    void tellsListenersAboutLinksAndTheLinksTheyReplace() throws IOException {
        LinkStore store = LinkStore.open(directory);
        List<LinkChange> changes = new CopyOnWriteArrayList<>();
        store.onChange(changes::add);
        Link steveToAlexDiscord =
                new Link(STEVE.minecraftId(), ALEX.discordId(), "Steve", Edition.JAVA, NOW.plusSeconds(60));

        store.link(STEVE);
        store.link(STEVE);
        store.link(steveToAlexDiscord);
        store.unlinkDiscord(ALEX.discordId());
        store.unlinkDiscord(ALEX.discordId());

        assertThat(changes)
                .containsExactly(
                        new LinkChange.Linked(STEVE),
                        new LinkChange.Linked(STEVE),
                        new LinkChange.Unlinked(STEVE),
                        new LinkChange.Linked(steveToAlexDiscord),
                        new LinkChange.Unlinked(steveToAlexDiscord));
    }

    @Test
    void keepsLinksAcrossRestarts() throws IOException {
        LinkStore store = LinkStore.open(directory);
        store.link(STEVE);
        store.link(ALEX);

        LinkStore reopened = LinkStore.open(directory);

        assertThat(reopened.all()).containsExactly(STEVE, ALEX);
        assertThat(reopened.readOnly()).isFalse();
    }

    @Test
    void linkingReplacesEarlierLinksOfEitherAccount() throws IOException {
        LinkStore store = LinkStore.open(directory);
        store.link(STEVE);
        store.link(ALEX);
        Link steveToAlexDiscord =
                new Link(STEVE.minecraftId(), ALEX.discordId(), "Steve", Edition.JAVA, NOW.plusSeconds(60));

        store.link(steveToAlexDiscord);

        assertThat(store.all()).containsExactly(steveToAlexDiscord);
        assertThat(store.byDiscordId(STEVE.discordId())).isEmpty();
        assertThat(store.byMinecraftId(ALEX.minecraftId())).isEmpty();
    }

    @Test
    void unlinkingRemovesBothDirections() throws IOException {
        LinkStore store = LinkStore.open(directory);
        store.link(STEVE);
        store.link(ALEX);

        assertThat(store.unlinkDiscord(STEVE.discordId())).contains(STEVE);
        assertThat(store.unlinkMinecraft(ALEX.minecraftId())).contains(ALEX);
        assertThat(store.unlinkMinecraft(ALEX.minecraftId())).isEmpty();

        assertThat(LinkStore.open(directory).all()).isEmpty();
    }

    @Test
    void goesReadOnlyAndNeverOverwritesADamagedFile() throws IOException {
        Path file = Files.createDirectories(directory.resolve("data")).resolve("links.json");
        String damaged = "{\"version\": 1, \"links\": [ {\"minecraftId\": ";
        Files.writeString(file, damaged);

        LinkStore store = LinkStore.open(directory);

        assertThat(store.readOnly()).isTrue();
        assertThat(store.all()).isEmpty();
        assertThatThrownBy(() -> store.link(STEVE))
                .isInstanceOf(ReadOnlyStoreException.class)
                .hasMessageContaining("Fix or delete that file, then restart the server");
        assertThat(file).hasContent(damaged);
        assertThat(store.byMinecraftId(STEVE.minecraftId())).isEmpty();
    }

    @Test
    void goesReadOnlyWhenAnEntryIsInvalid() throws IOException {
        Path file = Files.createDirectories(directory.resolve("data")).resolve("links.json");
        Files.writeString(file, """
                {"version": 1, "links": [{"minecraftId": "not-a-uuid", "discordId": "100000000000000001",
                "minecraftName": "Steve", "edition": "java", "linkedAt": "2026-10-10T12:00:00Z"}]}
                """);

        assertThat(LinkStore.open(directory).readOnly()).isTrue();
    }

    @Test
    void goesReadOnlyForAFileFromANewerFormat() throws IOException {
        Path file = Files.createDirectories(directory.resolve("data")).resolve("links.json");
        Files.writeString(file, "{\"version\": 2, \"links\": []}");

        assertThat(LinkStore.open(directory).readOnly()).isTrue();
    }

    @Test
    void writesKeysWithoutASpaceBeforeTheColon() throws IOException {
        LinkStore.open(directory).link(STEVE);

        assertThat(Files.readString(directory.resolve("data").resolve("links.json")))
                .contains("\"version\": 1")
                .doesNotContain("\" :");
    }

    @Test
    void keepsTheLastThreeVersionsAsBackupsInTheirOwnFolder() throws IOException {
        LinkStore store = LinkStore.open(directory);
        Path file = directory.resolve("data").resolve("links.json");
        store.link(STEVE);
        store.link(ALEX);
        String second = Files.readString(file);
        store.unlinkMinecraft(STEVE.minecraftId());
        String third = Files.readString(file);
        store.unlinkMinecraft(ALEX.minecraftId());
        String fourth = Files.readString(file);

        store.link(STEVE);

        assertThat(Files.readString(backup(file, 1))).isEqualTo(fourth);
        assertThat(Files.readString(backup(file, 2))).isEqualTo(third);
        assertThat(Files.readString(backup(file, 3))).isEqualTo(second);
        assertThat(backup(file, 4)).doesNotExist();
    }

    private static Path backup(Path file, int index) {
        return file.resolveSibling("backups").resolve("links.json." + index + ".bak");
    }
}
