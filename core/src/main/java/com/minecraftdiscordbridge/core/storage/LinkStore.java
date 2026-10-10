package com.minecraftdiscordbridge.core.storage;

import com.minecraftdiscordbridge.core.discord.DiscordId;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

public final class LinkStore {
    private final StoreFile file;
    private volatile Index index;

    private LinkStore(StoreFile file) {
        this.file = file;
        this.index = file.load(LinksJson.class, LinkStore::decode, Index.EMPTY);
    }

    public static LinkStore open(Path dataDirectory) {
        return new LinkStore(StoreFile.withBackups(dataDirectory, "links.json", "Account links"));
    }

    public boolean readOnly() {
        return file.readOnly();
    }

    public Optional<Link> byMinecraftId(UUID minecraftId) {
        return Optional.ofNullable(index.byMinecraft().get(minecraftId));
    }

    public Optional<Link> byDiscordId(DiscordId discordId) {
        return Optional.ofNullable(index.byDiscord().get(discordId));
    }

    public List<Link> all() {
        return index.links();
    }

    public synchronized void link(Link link) throws IOException {
        List<Link> kept = without(
                index.links(),
                existing -> existing.minecraftId().equals(link.minecraftId())
                        || existing.discordId().equals(link.discordId()));
        kept.add(link);
        replace(kept);
    }

    public synchronized Optional<Link> unlinkMinecraft(UUID minecraftId) throws IOException {
        return unlink(byMinecraftId(minecraftId));
    }

    public synchronized Optional<Link> unlinkDiscord(DiscordId discordId) throws IOException {
        return unlink(byDiscordId(discordId));
    }

    private Optional<Link> unlink(Optional<Link> found) throws IOException {
        if (found.isPresent()) {
            Link removed = found.get();
            replace(without(index.links(), removed::equals));
        }
        return found;
    }

    private void replace(List<Link> links) throws IOException {
        Index next = Index.of(links);
        file.save(encode(next.links()));
        index = next;
    }

    private static List<Link> without(List<Link> links, Predicate<Link> removed) {
        List<Link> kept = new ArrayList<>(links);
        kept.removeIf(removed);
        return kept;
    }

    private static Index decode(LinksJson json) {
        List<Link> links = new ArrayList<>();
        for (LinkJson entry : json.links()) {
            links.add(new Link(
                    UUID.fromString(entry.minecraftId()),
                    DiscordId.require(entry.discordId(), "discordId"),
                    entry.minecraftName(),
                    Edition.fromId(entry.edition()),
                    Instant.parse(entry.linkedAt())));
        }
        return Index.of(links);
    }

    private static LinksJson encode(List<Link> links) {
        List<LinkJson> entries = new ArrayList<>();
        for (Link link : links) {
            entries.add(new LinkJson(
                    link.minecraftId().toString(),
                    link.discordId().toString(),
                    link.minecraftName(),
                    link.edition().id(),
                    link.linkedAt().toString()));
        }
        return new LinksJson(StoreFile.FORMAT, entries);
    }

    private record Index(List<Link> links, Map<UUID, Link> byMinecraft, Map<DiscordId, Link> byDiscord) {
        static final Index EMPTY = new Index(List.of(), Map.of(), Map.of());

        static Index of(List<Link> links) {
            Map<UUID, Link> byMinecraft = new HashMap<>();
            Map<DiscordId, Link> byDiscord = new HashMap<>();
            for (Link link : links) {
                if (byMinecraft.put(link.minecraftId(), link) != null) {
                    throw new IllegalArgumentException("Minecraft account " + link.minecraftId() + " is linked twice");
                }
                if (byDiscord.put(link.discordId(), link) != null) {
                    throw new IllegalArgumentException("Discord account " + link.discordId() + " is linked twice");
                }
            }
            return new Index(List.copyOf(links), Map.copyOf(byMinecraft), Map.copyOf(byDiscord));
        }
    }

    record LinksJson(int version, List<LinkJson> links) implements Versioned {}

    record LinkJson(String minecraftId, String discordId, String minecraftName, String edition, String linkedAt) {}
}
