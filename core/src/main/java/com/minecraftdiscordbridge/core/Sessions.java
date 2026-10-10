package com.minecraftdiscordbridge.core;

import static com.minecraftdiscordbridge.core.log.Log.LOGGER;

import com.minecraftdiscordbridge.core.access.AccessContext;
import com.minecraftdiscordbridge.core.access.JoinGate;
import com.minecraftdiscordbridge.core.access.RoleSync;
import com.minecraftdiscordbridge.core.actions.Actions;
import com.minecraftdiscordbridge.core.actions.GameActions;
import com.minecraftdiscordbridge.core.chat.ChatToggle;
import com.minecraftdiscordbridge.core.chat.DiscordChat;
import com.minecraftdiscordbridge.core.chat.MinecraftChat;
import com.minecraftdiscordbridge.core.commands.GameCommands;
import com.minecraftdiscordbridge.core.commands.SlashRouter;
import com.minecraftdiscordbridge.core.config.BridgeConfig;
import com.minecraftdiscordbridge.core.config.ConfigSnapshot;
import com.minecraftdiscordbridge.core.console.ConsoleInput;
import com.minecraftdiscordbridge.core.console.ConsoleMirror;
import com.minecraftdiscordbridge.core.discord.DiscordId;
import com.minecraftdiscordbridge.core.events.EventPosts;
import com.minecraftdiscordbridge.core.i18n.Lang;
import com.minecraftdiscordbridge.core.i18n.Replies;
import com.minecraftdiscordbridge.core.linking.Linking;
import com.minecraftdiscordbridge.core.setup.ChannelSetup;
import com.minecraftdiscordbridge.core.setup.Channels;
import com.minecraftdiscordbridge.core.status.StatusBoard;
import com.minecraftdiscordbridge.core.storage.Stores;
import com.minecraftdiscordbridge.core.text.Format;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

final class Sessions {
    private Sessions() {}

    static Session build(
            Services services, Stores stores, ConfigSnapshot snapshot, Channels channels, Runnable rebound) {
        BridgeConfig config = snapshot.config();
        Lang lang = Lang.of(config.language());
        Replies replies = new Replies(lang, services.platform().info().dataVersion());
        Optional<DiscordId> chat = channels.chat();
        Optional<DiscordId> events = channels.events();
        boolean hasGuild = !DiscordPlan.channelIds(config, channels).isEmpty();
        ChannelSetup setup = new ChannelSetup(
                services.discord(),
                stores.bindings(),
                config.discord(),
                services.io(),
                services.setupCodes(),
                services.setupAttempts(),
                services.clock(),
                replies,
                rebound);
        Linking linking = new Linking(
                config.linking(),
                stores.links(),
                services.io(),
                services.pendingLinks(),
                services.linkAttempts(),
                services.roster(),
                services.clock(),
                replies,
                () -> hasGuild && services.discord().status().isConnected());
        GameActions gameActions =
                new GameActions(services.platform(), services.loop()::control, services.actionTimeout());
        Actions actions = new Actions(config.actions(), gameActions, lang);
        AccessContext access = services.accessContext(stores);
        return new Session(
                replies,
                new MinecraftChat(config.chat(), chat, services.discord(), stores.links()::all),
                new DiscordChat(
                        config.chat(),
                        chat,
                        chatFormat(config.chat().format()),
                        lang,
                        services.platform(),
                        stores.preferences()::hidingDiscord),
                new EventPosts(
                        config.events(), events, lang, services.vanilla().use(config.language()), services.discord()),
                new StatusBoard(config.status(), chat, lang, services.discord(), services.roster()),
                new SlashRouter(setup, linking, actions, services.roster(), lang),
                new GameCommands(replies, linking, new ChatToggle(stores.preferences(), services.io(), replies), setup),
                ConsoleMirror.start(
                        config.console(),
                        snapshot.secrets().hasDiscordToken() ? config.discord().consoleChannel() : Optional.empty(),
                        services.discord(),
                        lang,
                        services.loop(),
                        () -> secrets(services, snapshot)),
                JoinGate.create(config.linking(), services.platform().info(), access, replies),
                RoleSync.create(config.roleSync(), services.platform().info(), access),
                new ConsoleInput(
                        config.console(),
                        config.discord().consoleChannel(),
                        gameActions,
                        services.discord(),
                        lang,
                        () -> secrets(services, snapshot)));
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

    private static Collection<String> secrets(Services services, ConfigSnapshot snapshot) {
        List<String> secrets = new ArrayList<>();
        if (snapshot.secrets().hasDiscordToken()) {
            secrets.add(snapshot.secrets().discordToken());
        }
        services.setupCodes().active(services.clock().instant()).ifPresent(secrets::add);
        secrets.addAll(services.pendingLinks().codes(services.clock().instant()));
        return secrets;
    }
}
