package com.minecraftdiscordbridge.core;

import com.minecraftdiscordbridge.core.actions.Actions;
import com.minecraftdiscordbridge.core.chat.ChatToggle;
import com.minecraftdiscordbridge.core.chat.DiscordChat;
import com.minecraftdiscordbridge.core.chat.MinecraftChat;
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
import com.minecraftdiscordbridge.core.text.Format;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class Sessions {
    private static final Logger LOGGER = LoggerFactory.getLogger("MinecraftDiscordBridge");

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
                new MinecraftChat(config.chat(), chat, shared.discord(), stores.links()::all),
                new DiscordChat(
                        config.chat(),
                        chat,
                        chatFormat(config.chat().format()),
                        lang,
                        shared.platform(),
                        stores.preferences()::hidingDiscord),
                new EventPosts(
                        config.events(), events, lang, shared.vanilla().use(config.language()), shared.discord()),
                new StatusBoard(config.status(), chat, lang, shared.discord(), shared.roster()),
                new SlashRouter(setup, linking, actions, shared.roster(), lang),
                new GameCommands(replies, linking, new ChatToggle(stores.preferences(), shared.io(), replies), setup));
    }

    private static boolean connected(DiscordStatus status) {
        return status.state() == DiscordStatus.State.READY || status.state() == DiscordStatus.State.DEGRADED;
    }

    private static Format chatFormat(String template) {
        try {
            Format format = DiscordChat.compileFormat(template);
            format.legacyCodeWarning().ifPresent(LOGGER::warn);
            return format;
        } catch (IllegalArgumentException invalid) {
            LOGGER.warn(
                    "{} Using the default chat.format; fix chat.format in config.yml and run /discord reload",
                    invalid.getMessage());
            return DiscordChat.compileFormat(BridgeConfig.defaults().chat().format());
        }
    }
}
