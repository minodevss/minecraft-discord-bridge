package com.minecraftdiscordbridge.core.fakes;

import com.minecraftdiscordbridge.core.platform.CommandSource;
import com.minecraftdiscordbridge.core.platform.Permission;
import com.minecraftdiscordbridge.core.text.TextRenderer;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.jspecify.annotations.Nullable;

public final class RecordingSource implements CommandSource {
    public final BlockingQueue<String> replies = new LinkedBlockingQueue<>();
    private final @Nullable UUID playerId;
    private final String name;
    private final Set<Permission> permissions;

    private RecordingSource(@Nullable UUID playerId, String name, Set<Permission> permissions) {
        this.playerId = playerId;
        this.name = name;
        this.permissions = permissions;
    }

    public static RecordingSource player(UUID id, String name) {
        return new RecordingSource(id, name, EnumSet.of(Permission.LINK, Permission.TOGGLE));
    }

    public static RecordingSource console() {
        return new RecordingSource(null, "CONSOLE", EnumSet.allOf(Permission.class));
    }

    public static RecordingSource operator(UUID id, String name) {
        return new RecordingSource(id, name, EnumSet.allOf(Permission.class));
    }

    @Override
    public @Nullable UUID playerId() {
        return playerId;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public boolean hasPermission(Permission permission) {
        return permissions.contains(permission);
    }

    @Override
    public void reply(String json) {
        replies.add(json);
    }

    public String nextPlain() {
        return TextRenderer.plain(TextRenderer.fromJson(Waits.take(replies)));
    }
}
