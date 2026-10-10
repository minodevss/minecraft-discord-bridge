package com.minecraftdiscordbridge.core;

import com.minecraftdiscordbridge.core.access.AccessContext;
import com.minecraftdiscordbridge.core.access.JoinGate;
import com.minecraftdiscordbridge.core.actions.Actions;
import com.minecraftdiscordbridge.core.chat.ChatToggle;
import com.minecraftdiscordbridge.core.chat.DiscordChat;
import com.minecraftdiscordbridge.core.chat.MinecraftChat;
import com.minecraftdiscordbridge.core.commands.GameCommands;
import com.minecraftdiscordbridge.core.commands.SlashRouter;
import com.minecraftdiscordbridge.core.config.BridgeConfig;
import com.minecraftdiscordbridge.core.config.Channels;
import com.minecraftdiscordbridge.core.config.ConfigSnapshot;
import com.minecraftdiscordbridge.core.console.ConsoleMirror;
import com.minecraftdiscordbridge.core.events.EventPosts;
import com.minecraftdiscordbridge.core.i18n.Lang;
import com.minecraftdiscordbridge.core.i18n.Replies;
import com.minecraftdiscordbridge.core.linking.Linking;
import com.minecraftdiscordbridge.core.setup.ChannelSetup;
import com.minecraftdiscordbridge.core.status.StatusBoard;
import com.minecraftdiscordbridge.core.storage.ChannelBinding;
import com.minecraftdiscordbridge.core.text.Format;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.OptionalLong;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class Sessions {
    private static final Logger LOGGER = LoggerFactory.getLogger("MinecraftDiscordBridge");
    private static final String DEFAULT_CHAT_FORMAT = "<gray>[Discord]</gray> <author>: <message>";

    private Sessions() {}

    static Session build(
            Shared shared,
            Stores stores,
            ConfigSnapshot snapshot,
            Channels channels,
            OptionalLong console,
            Consumer<ChannelBinding> rebound) {
        BridgeConfig config = snapshot.config();
        Lang lang = Lang.of(config.language());
        Replies replies = new Replies(lang, shared.platform().info().dataVersion());
        OptionalLong chat = Ids.parse(channels.chat());
        OptionalLong events = Ids.parse(channels.events());
        ChannelSetup setup = new ChannelSetup(
                shared.discord(),
                stores.bindings(),
                config.discord(),
                Ids.parse(channels.console()),
                shared.io(),
                shared.setupCodes(),
                shared.attempts(),
                shared.clock(),
                replies,
                rebound);
        Linking linking = new Linking(
                config.linking(),
                stores.links(),
                shared.io(),
                shared.pendingLinks(),
                shared.attempts(),
                shared.roster(),
                shared.clock(),
                replies);
        Actions actions =
                new Actions(config.actions(), shared.platform(), shared.loop()::control, lang, shared.actionTimeout());
        AccessContext access = shared.accessContext(stores);
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
                new EventPosts(config.events(), events, lang, shared.discord()),
                new StatusBoard(config.status(), chat, lang, shared.discord(), shared.roster()),
                new SlashRouter(setup, linking, actions, shared.roster(), lang),
                new GameCommands(replies, linking, new ChatToggle(stores.preferences(), shared.io(), replies), setup),
                ConsoleMirror.start(
                        config.console(),
                        console,
                        shared.discord(),
                        lang,
                        shared.loop(),
                        () -> secrets(shared, snapshot)),
                JoinGate.create(config.linking(), shared.platform().info(), access, replies));
    }

    private static Format chatFormat(String template) {
        try {
            return DiscordChat.compileFormat(template);
        } catch (IllegalArgumentException invalid) {
            LOGGER.warn("{} Using the default chat.format until it is fixed", invalid.getMessage());
            return DiscordChat.compileFormat(DEFAULT_CHAT_FORMAT);
        }
    }

    private static Collection<String> secrets(Shared shared, ConfigSnapshot snapshot) {
        List<String> secrets = new ArrayList<>();
        if (snapshot.secrets().hasDiscordToken()) {
            secrets.add(snapshot.secrets().discordToken());
        }
        shared.setupCodes().active(shared.clock().instant()).ifPresent(secrets::add);
        return secrets;
    }
}
