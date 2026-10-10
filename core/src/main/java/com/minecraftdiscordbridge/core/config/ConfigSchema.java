package com.minecraftdiscordbridge.core.config;

import com.minecraftdiscordbridge.core.discord.DiscordId;
import com.minecraftdiscordbridge.core.i18n.Language;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class ConfigSchema {
    static final int VERSION = 1;
    static final String VERSION_KEY = "config-version";
    static final List<String> VERSION_COMMENT = List.of("Used to upgrade this file. Do not change it.");
    static final List<String> HEADER = List.of(
            "Minecraft Discord Bridge settings. The bot token goes in secrets.yml.",
            "Run /discord reload after you edit this file.",
            "To copy an ID, turn on Developer Mode in Discord (User Settings > Advanced),",
            "then right-click the channel, role or user and click Copy ID.");

    static final Option<Language> LANGUAGE = choice(
            "language",
            Language.EN,
            "Language of the bridge's messages, and of deaths and advancements on Discord: en or ko.");

    static final Option<String> CHAT_CHANNEL =
            discordId("chat.channel", "Channel ID for chat. Empty: run /setup in the channel instead.");
    static final Option<Boolean> MINECRAFT_TO_DISCORD = flag("chat.minecraft-to-discord", true);
    static final Option<Boolean> DISCORD_TO_MINECRAFT = flag("chat.discord-to-minecraft", true);
    static final Option<String> FORMAT = text(
            "chat.format",
            "<gray>[Discord]</gray> <author>: <message>",
            "How Discord messages look in game, in MiniMessage. <author> is the sender, <message> the text.");
    static final Option<String> AVATAR_URL = text(
            "chat.avatar-url",
            "https://crafthead.net/helm/{texture}",
            "Player head on Discord. {texture} is the skin (the UUID if unknown); {uuid} and {name} also work.",
            "Empty uses Discord's default avatar.");
    static final Option<Boolean> PING_LINKED_PLAYERS =
            flag("chat.ping-linked-players", false, "Let @name in game ping that player's linked Discord account.");
    static final Option<List<String>> BLOCKED_WORDS = new Option<>(
            "chat.blocked-words",
            ValueTypes.listOf(ValueTypes.REGEX),
            List.of(),
            List.of(
                    "Messages that match one of these regular expressions are not sent either way.",
                    "Example: blocked-words: [\"(?i)badword\"]"));

    static final Option<String> EVENTS_CHANNEL = discordId(
            "events.channel", "Channel ID for events. Empty: the /setup as:events channel, else the chat channel.");
    static final Option<Boolean> JOIN = flag("events.join", true);
    static final Option<Boolean> LEAVE = flag("events.leave", true);
    static final Option<Boolean> DEATH = flag("events.death", true);
    static final Option<Boolean> ADVANCEMENT = flag("events.advancement", true);
    static final Option<Boolean> SERVER_START = flag("events.server-start", true);
    static final Option<Boolean> SERVER_STOP = flag("events.server-stop", true);
    static final Option<String> SERVER_START_PING_ROLE =
            discordId("events.server-start-ping-role", "Role ID to ping when the server starts. Empty pings nobody.");

    static final Option<Boolean> BOT_STATUS = flag("player-count.bot-status", true);
    static final Option<Boolean> CHANNEL_TOPIC = flag("player-count.channel-topic", false);

    static final Option<Boolean> LINKING_ENABLED =
            flag("linking.enabled", true, "Players link with /discord link in game and /link in Discord.");
    static final Option<Boolean> REQUIRE_FOR_JOIN = flag(
            "linking.require-for-join",
            false,
            "Only linked players can join. The minecraftdiscordbridge.gate.bypass permission skips this.");
    static final Option<String> REQUIRED_ROLE = discordId(
            "linking.required-role", "Role ID a player also needs to join. Setting it turns on require-for-join.");
    static final Option<UnreachablePolicy> WHEN_DISCORD_UNREACHABLE = choice(
            "linking.when-discord-unreachable",
            UnreachablePolicy.DENY,
            "deny or allow joins when Discord cannot be reached to check required-role.");

    static final Option<String> CONSOLE_CHANNEL = discordId(
            "console.channel",
            "Channel ID that mirrors the server console, with tokens, IPs and passwords hidden. Empty: off.");
    static final Option<List<String>> INPUT_USERS = new Option<>(
            "console.input-users",
            ValueTypes.listOf(ValueTypes.DISCORD_ID),
            List.of(),
            List.of(
                    "Discord user IDs that may run console commands in that channel. Empty: nobody.",
                    "Example: input-users: [\"123456789012345678\"]"));

    static final Option<Boolean> WHITELIST = flag("discord-commands.whitelist", false);
    static final Option<Boolean> KICK = flag("discord-commands.kick", false);

    static final Option<List<RoleSyncRule>> ROLE_SYNC = new Option<>(
            "role-sync",
            ValueTypes.listOf(ValueTypes.ROLE_SYNC_RULE),
            List.of(),
            List.of(
                    "Pair Discord roles with LuckPerms groups. direction is discord-to-minecraft",
                    "(the default, can be left out) or minecraft-to-discord. Example:",
                    "  role-sync:",
                    "    - discord-role: \"123456789012345678\"",
                    "      group: \"vip\""));

    static final List<Block> LAYOUT = List.of(
            LANGUAGE,
            new Section(
                    "chat",
                    List.of(),
                    List.of(
                            CHAT_CHANNEL,
                            MINECRAFT_TO_DISCORD,
                            DISCORD_TO_MINECRAFT,
                            FORMAT,
                            AVATAR_URL,
                            PING_LINKED_PLAYERS,
                            BLOCKED_WORDS)),
            new Section(
                    "events",
                    List.of("Set any event to false to stop posting it. On Velocity, join also posts server switches."),
                    List.of(
                            EVENTS_CHANNEL,
                            JOIN,
                            LEAVE,
                            DEATH,
                            ADVANCEMENT,
                            SERVER_START,
                            SERVER_STOP,
                            SERVER_START_PING_ROLE)),
            new Section(
                    "player-count",
                    List.of("Show the player count as the bot's status and in the chat channel's topic."),
                    List.of(BOT_STATUS, CHANNEL_TOPIC)),
            new Section(
                    "linking",
                    List.of(),
                    List.of(LINKING_ENABLED, REQUIRE_FOR_JOIN, REQUIRED_ROLE, WHEN_DISCORD_UNREACHABLE)),
            new Section("console", List.of(), List.of(CONSOLE_CHANNEL, INPUT_USERS)),
            new Section(
                    "discord-commands",
                    List.of(
                            "/whitelist and /kick in Discord. Only Discord admins can use them",
                            "unless you allow more roles in Server Settings > Integrations."),
                    List.of(WHITELIST, KICK)),
            ROLE_SYNC);

    static final List<Section> SECTIONS = LAYOUT.stream()
            .flatMap(block -> block instanceof Section section ? Stream.of(section) : Stream.empty())
            .toList();

    static final List<Option<?>> OPTIONS = LAYOUT.stream()
            .flatMap(block ->
                    block instanceof Section section ? section.options().stream() : Stream.of((Option<?>) block))
            .toList();

    static final Set<String> OPTION_PATHS = OPTIONS.stream().map(Option::path).collect(Collectors.toUnmodifiableSet());

    private ConfigSchema() {}

    static BridgeConfig toConfig(ConfigValues values) {
        return new BridgeConfig(
                values.get(LANGUAGE),
                new ChatConfig(
                        DiscordId.parse(values.get(CHAT_CHANNEL)),
                        values.get(MINECRAFT_TO_DISCORD),
                        values.get(DISCORD_TO_MINECRAFT),
                        values.get(FORMAT),
                        values.get(AVATAR_URL),
                        values.get(PING_LINKED_PLAYERS),
                        values.get(BLOCKED_WORDS).stream().map(Pattern::compile).toList()),
                new EventsConfig(
                        DiscordId.parse(values.get(EVENTS_CHANNEL)),
                        values.get(JOIN),
                        values.get(LEAVE),
                        values.get(DEATH),
                        values.get(ADVANCEMENT),
                        values.get(SERVER_START),
                        values.get(SERVER_STOP),
                        DiscordId.parse(values.get(SERVER_START_PING_ROLE))),
                new PlayerCountConfig(values.get(BOT_STATUS), values.get(CHANNEL_TOPIC)),
                new LinkingConfig(
                        values.get(LINKING_ENABLED),
                        values.get(REQUIRE_FOR_JOIN),
                        DiscordId.parse(values.get(REQUIRED_ROLE)),
                        values.get(WHEN_DISCORD_UNREACHABLE)),
                new ConsoleConfig(DiscordId.parse(values.get(CONSOLE_CHANNEL)), ids(values.get(INPUT_USERS))),
                new DiscordCommandsConfig(values.get(WHITELIST), values.get(KICK)),
                values.get(ROLE_SYNC));
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
