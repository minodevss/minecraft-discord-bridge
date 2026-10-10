package com.minecraftdiscordbridge.core.config;

import com.minecraftdiscordbridge.core.discord.DiscordId;
import com.minecraftdiscordbridge.core.i18n.Language;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

final class ConfigSchema {
    static final int VERSION = 1;
    static final String VERSION_KEY = "config-version";
    static final List<String> VERSION_COMMENT = List.of("Used to upgrade this file. Do not change it.");
    static final List<String> HEADER = List.of(
            "Minecraft Discord Bridge settings.",
            "The bot token goes in secrets.yml, not here.",
            "Changes take effect after a reload or a restart.");
    static final List<Migration> MIGRATIONS = List.of();

    static final Option<Language> LANGUAGE =
            choice("language", Language.EN, "Language of the bridge's own messages: en (English) or ko (Korean).");

    static final Option<String> CHAT_CHANNEL =
            discordId("discord.chat-channel", "Channel for chat. Leave empty to choose it with /setup in Discord.");
    static final Option<String> EVENTS_CHANNEL = discordId(
            "discord.events-channel",
            "Channel for joins, leaves, deaths, advancements and server start and stop.",
            "Empty uses the chat channel.");
    static final Option<String> CONSOLE_CHANNEL =
            discordId("discord.console-channel", "Channel that shows the server console. Empty turns it off.");
    static final Option<Boolean> AUTO_ENABLE_MESSAGE_CONTENT = flag(
            "discord.auto-enable-message-content",
            true,
            "Try to turn on the bot's Message Content intent when it is off.",
            "The bridge needs it to read messages from Discord.");

    static final Option<Boolean> MINECRAFT_TO_DISCORD =
            flag("chat.minecraft-to-discord", true, "Send Minecraft chat to Discord.");
    static final Option<Boolean> DISCORD_TO_MINECRAFT =
            flag("chat.discord-to-minecraft", true, "Send Discord chat to Minecraft.");
    static final Option<String> FORMAT = text(
            "chat.format",
            "<gray>[Discord]</gray> <author>: <message>",
            "How Discord messages look in Minecraft, in MiniMessage format.",
            "<author> is the sender's name and <message> is the text.");
    static final Option<String> AVATAR_URL = text(
            "chat.avatar-url",
            "https://crafthead.net/helm/{texture}",
            "Avatar for player messages on Discord. {texture} is the player's skin texture ID.");
    static final Option<String> AVATAR_URL_FALLBACK = text(
            "chat.avatar-url-fallback",
            "https://crafthead.net/helm/{uuid}",
            "Avatar to use when the skin texture is not known. {uuid} is the player's UUID.");
    static final Option<Boolean> PING_LINKED_PLAYERS = flag(
            "chat.ping-linked-players",
            false,
            "Let @name in Minecraft chat ping that player's linked Discord account.");
    static final Option<List<String>> BLOCKED_WORDS = new Option<>(
            "chat.blocked-words",
            ValueTypes.listOf(ValueTypes.REGEX),
            List.of(),
            List.of(
                    "Messages that match one of these regular expressions are not relayed.",
                    "Example: blocked-words: [\"(?i)badword\"]"));

    static final Option<Boolean> JOIN = flag("events.join", true, "Post when a player joins.");
    static final Option<Boolean> LEAVE = flag("events.leave", true, "Post when a player leaves.");
    static final Option<Boolean> DEATH = flag("events.death", true, "Post when a player dies.");
    static final Option<Boolean> ADVANCEMENT =
            flag("events.advancement", true, "Post when a player earns an advancement.");
    static final Option<Boolean> SERVER_START = flag("events.server-start", true, "Post when the server starts.");
    static final Option<Boolean> SERVER_STOP = flag("events.server-stop", true, "Post when the server stops.");
    static final Option<String> SERVER_START_PING_ROLE =
            discordId("events.server-start-ping-role", "Role to ping when the server starts. Empty pings nobody.");

    static final Option<Boolean> PRESENCE = flag("status.presence", true, "Show the player count as the bot's status.");
    static final Option<Boolean> TOPIC =
            flag("status.topic", false, "Show the player count in the chat channel's topic.");

    static final Option<Boolean> LINKING_ENABLED =
            flag("linking.enabled", true, "Let players link their Minecraft and Discord accounts.");
    static final Option<Boolean> REQUIRE_FOR_JOIN =
            flag("linking.require-for-join", false, "Only players who linked their account can join.");
    static final Option<String> REQUIRED_ROLE = discordId(
            "linking.required-role", "Discord role a linked player must have to join. Empty means no role is needed.");
    static final Option<UnreachablePolicy> WHEN_DISCORD_UNREACHABLE = choice(
            "linking.when-discord-unreachable",
            UnreachablePolicy.DENY,
            "When linking is required and Discord cannot be reached: deny or allow joins.");

    static final Option<List<RoleSyncRule>> ROLE_SYNC_RULES = new Option<>(
            "role-sync.rules",
            ValueTypes.listOf(ValueTypes.ROLE_SYNC_RULE),
            List.of(),
            List.of(
                    "Each rule pairs a Discord role with a permission group.",
                    "direction is discord-to-minecraft or minecraft-to-discord. Example:",
                    "  rules:",
                    "    - discord-role: \"123456789012345678\"",
                    "      group: \"vip\"",
                    "      direction: \"discord-to-minecraft\""));

    static final Option<Boolean> WHITELIST =
            flag("actions.whitelist", false, "Allow Discord commands that add and remove players from the whitelist.");
    static final Option<Boolean> KICK = flag("actions.kick", false, "Allow a Discord command that kicks players.");

    static final Option<Boolean> MIRROR =
            flag("console.mirror", false, "Copy the server console to the console channel.");
    static final Option<List<String>> INPUT_USERS = new Option<>(
            "console.input-users",
            ValueTypes.listOf(ValueTypes.DISCORD_ID),
            List.of(),
            List.of(
                    "Discord user IDs that can run console commands from the console channel.",
                    "Empty means nobody. Example: input-users: [\"123456789012345678\"]"));

    static final List<Option<?>> OPTIONS = List.of(
            LANGUAGE,
            CHAT_CHANNEL,
            EVENTS_CHANNEL,
            CONSOLE_CHANNEL,
            AUTO_ENABLE_MESSAGE_CONTENT,
            MINECRAFT_TO_DISCORD,
            DISCORD_TO_MINECRAFT,
            FORMAT,
            AVATAR_URL,
            AVATAR_URL_FALLBACK,
            PING_LINKED_PLAYERS,
            BLOCKED_WORDS,
            JOIN,
            LEAVE,
            DEATH,
            ADVANCEMENT,
            SERVER_START,
            SERVER_STOP,
            SERVER_START_PING_ROLE,
            PRESENCE,
            TOPIC,
            LINKING_ENABLED,
            REQUIRE_FOR_JOIN,
            REQUIRED_ROLE,
            WHEN_DISCORD_UNREACHABLE,
            ROLE_SYNC_RULES,
            WHITELIST,
            KICK,
            MIRROR,
            INPUT_USERS);

    static final List<Section> SECTIONS = List.of(
            new Section(
                    "discord",
                    List.of(
                            "To copy an ID, turn on Developer Mode in Discord (User Settings > Advanced),",
                            "then right-click the channel, role or user and copy its ID.")),
            new Section("chat", List.of()),
            new Section("events", List.of("Messages posted to the events channel.")),
            new Section("status", List.of()),
            new Section("linking", List.of()),
            new Section("role-sync", List.of("Keeps Discord roles and permission groups in step.")),
            new Section("actions", List.of()),
            new Section("console", List.of()));

    static final Set<String> OPTION_PATHS = OPTIONS.stream().map(Option::path).collect(Collectors.toUnmodifiableSet());

    private ConfigSchema() {}

    static BridgeConfig toConfig(ConfigValues values) {
        return new BridgeConfig(
                values.get(LANGUAGE),
                new DiscordConfig(
                        DiscordId.parse(values.get(CHAT_CHANNEL)),
                        DiscordId.parse(values.get(EVENTS_CHANNEL)),
                        DiscordId.parse(values.get(CONSOLE_CHANNEL)),
                        values.get(AUTO_ENABLE_MESSAGE_CONTENT)),
                new ChatConfig(
                        values.get(MINECRAFT_TO_DISCORD),
                        values.get(DISCORD_TO_MINECRAFT),
                        values.get(FORMAT),
                        values.get(AVATAR_URL),
                        values.get(AVATAR_URL_FALLBACK),
                        values.get(PING_LINKED_PLAYERS),
                        values.get(BLOCKED_WORDS).stream().map(Pattern::compile).toList()),
                new EventsConfig(
                        values.get(JOIN),
                        values.get(LEAVE),
                        values.get(DEATH),
                        values.get(ADVANCEMENT),
                        values.get(SERVER_START),
                        values.get(SERVER_STOP),
                        DiscordId.parse(values.get(SERVER_START_PING_ROLE))),
                new StatusConfig(values.get(PRESENCE), values.get(TOPIC)),
                new LinkingConfig(
                        values.get(LINKING_ENABLED),
                        values.get(REQUIRE_FOR_JOIN),
                        DiscordId.parse(values.get(REQUIRED_ROLE)),
                        values.get(WHEN_DISCORD_UNREACHABLE)),
                new RoleSyncConfig(values.get(ROLE_SYNC_RULES)),
                new ActionsConfig(values.get(WHITELIST), values.get(KICK)),
                new ConsoleConfig(values.get(MIRROR), ids(values.get(INPUT_USERS))));
    }

    private static List<DiscordId> ids(List<String> values) {
        return values.stream().flatMap(value -> DiscordId.parse(value).stream()).toList();
    }

    private static Option<Boolean> flag(String path, boolean value, String... comment) {
        return new Option<>(path, ValueTypes.BOOLEAN, value, List.of(comment));
    }

    private static Option<String> text(String path, String value, String... comment) {
        return new Option<>(path, ValueTypes.TEXT, value, List.of(comment));
    }

    private static Option<String> discordId(String path, String... comment) {
        return new Option<>(path, ValueTypes.DISCORD_ID_OR_EMPTY, "", List.of(comment));
    }

    private static <E extends Enum<E>> Option<E> choice(String path, E value, String... comment) {
        return new Option<>(path, ValueTypes.choice(value.getDeclaringClass()), value, List.of(comment));
    }
}
