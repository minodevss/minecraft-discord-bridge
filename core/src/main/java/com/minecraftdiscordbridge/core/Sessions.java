package com.minecraftdiscordbridge.core;

import com.minecraftdiscordbridge.core.actions.Actions;
import com.minecraftdiscordbridge.core.chat.ChatToggle;
import com.minecraftdiscordbridge.core.commands.GameCommands;
import com.minecraftdiscordbridge.core.commands.SlashRouter;
import com.minecraftdiscordbridge.core.config.BridgeConfig;
import com.minecraftdiscordbridge.core.config.Channels;
import com.minecraftdiscordbridge.core.config.ConfigSnapshot;
import com.minecraftdiscordbridge.core.discord.DiscordId;
import com.minecraftdiscordbridge.core.discord.DiscordStatus;
import com.minecraftdiscordbridge.core.events.EventPosts;
import com.minecraftdiscordbridge.core.i18n.Lang;
import com.minecraftdiscordbridge.core.i18n.Replies;
import com.minecraftdiscordbridge.core.linking.Linking;
import com.minecraftdiscordbridge.core.setup.ChannelSetup;
import com.minecraftdiscordbridge.core.status.StatusBoard;
import java.util.Optional;

final class Sessions {
    private Sessions() {}

    static Session build(Shared shared, Stores stores, ConfigSnapshot snapshot, Channels channels, Runnable rebound) {
        BridgeConfig config = snapshot.config();
        Lang lang = Lang.of(config.language());
        Replies replies = new Replies(lang, shared.platform().info().dataVersion());
        Optional<DiscordId> chat = channels.chat();
        Optional<DiscordId> events = channels.events();
        boolean hasGuild = !DiscordPlan.channelIds(config, channels).isEmpty();
        ChannelSetup setup = new ChannelSetup(
                shared.discord(),
                stores.bindings(),
                config.discord(),
                shared.io(),
                shared.setupCodes(),
                shared.setupAttempts(),
                shared.clock(),
                replies,
                rebound);
        Linking linking = new Linking(
                config.linking(),
                stores.links(),
                shared.io(),
                shared.pendingLinks(),
                shared.linkAttempts(),
                shared.roster(),
                shared.clock(),
                replies,
                () -> hasGuild && connected(shared.discord().status()));
        Actions actions =
                new Actions(config.actions(), shared.platform(), shared.loop()::control, lang, shared.actionTimeout());
        return new Session(
                replies,
                new EventPosts(
                        config.events(), events, lang, shared.vanilla().use(config.language()), shared.discord()),
                new StatusBoard(config.status(), chat, lang, shared.discord(), shared.roster()),
                new SlashRouter(setup, linking, actions, shared.roster(), lang),
                new GameCommands(replies, linking, new ChatToggle(stores.preferences(), shared.io(), replies), setup));
    }

    private static boolean connected(DiscordStatus status) {
        return status.state() == DiscordStatus.State.READY || status.state() == DiscordStatus.State.DEGRADED;
    }
}
