package com.minecraftdiscordbridge.core.chat;

import com.minecraftdiscordbridge.core.config.ChatConfig;
import com.minecraftdiscordbridge.core.discord.Author;
import com.minecraftdiscordbridge.core.discord.Discord;
import com.minecraftdiscordbridge.core.discord.DiscordId;
import com.minecraftdiscordbridge.core.discord.OutboundMessage;
import com.minecraftdiscordbridge.core.platform.GameEvent;
import com.minecraftdiscordbridge.core.platform.PlayerProfile;
import com.minecraftdiscordbridge.core.storage.Link;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

public final class MinecraftChat {
    private final ChatConfig config;
    private final Optional<DiscordId> channel;
    private final Discord discord;
    private final Supplier<List<Link>> links;

    public MinecraftChat(ChatConfig config, Optional<DiscordId> channel, Discord discord, Supplier<List<Link>> links) {
        this.config = config;
        this.channel = channel;
        this.discord = discord;
        this.links = links;
    }

    public void onChat(GameEvent.PlayerChatted chat) {
        if (!config.minecraftToDiscord() || channel.isEmpty() || chat.message().isBlank()) {
            return;
        }
        if (Blocklist.blocks(config.blockedWords(), chat.message())) {
            return;
        }
        PlayerProfile player = chat.player();
        Author author = Author.player(player.id().toString(), player.name(), Avatars.url(config, player));
        PlayerPings.Pinged text = config.pingLinkedPlayers()
                ? PlayerPings.escape(chat.message(), links.get())
                : PlayerPings.escape(chat.message(), List.of());
        discord.send(new OutboundMessage(channel.get(), author, text.text(), text.userIds(), Set.of()));
    }
}
