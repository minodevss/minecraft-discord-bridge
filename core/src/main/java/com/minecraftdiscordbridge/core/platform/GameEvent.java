package com.minecraftdiscordbridge.core.platform;

import java.util.List;
import org.jspecify.annotations.Nullable;

public sealed interface GameEvent {
    record ServerStarted(List<PlayerProfile> online) implements GameEvent {
        public ServerStarted {
            online = List.copyOf(online);
        }
    }

    record ServerStopping() implements GameEvent {}

    record ServerStopped() implements GameEvent {}

    record PlayerJoined(PlayerProfile player, boolean vanished) implements GameEvent {}

    record PlayerLeft(PlayerProfile player, boolean vanished) implements GameEvent {}

    record PlayerSwitchedServer(
            PlayerProfile player, @Nullable String from, String to) implements GameEvent {}

    record PlayerChatted(PlayerProfile player, String message) implements GameEvent {}

    record PlayerDied(PlayerProfile player, RichText message) implements GameEvent {}

    record AdvancementEarned(PlayerProfile player, RichText title, AdvancementFrame frame) implements GameEvent {}
}
