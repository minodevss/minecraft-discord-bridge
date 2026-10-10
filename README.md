# Minecraft Discord Bridge

Connect one Minecraft server, or one Velocity network, to one Discord server. Chat goes both ways, game events show up in a channel, and players can link their accounts. The bridge runs inside the server as a plugin or mod, and you run your own Discord bot. The server never waits for Discord: it starts while the bridge connects in the background, and the bridge finishes stopping within a few seconds.

- Chat both ways. Minecraft messages appear in Discord under the player's name and head; Discord messages appear in game.
- Joins, leaves, deaths, advancements and server start and stop, in the chat channel or a separate events channel.
- The player count as the bot's status or the channel topic.
- Account linking with a one-time code.
- `/list` in Discord, and `/whitelist` and `/kick` if you turn them on.
- A console channel that mirrors the server console, with tokens, webhook URLs, IP addresses and passwords hidden.
- English and Korean.

## Install

Download the jar for your server from the [latest release](https://github.com/minodevss/minecraft-discord-bridge/releases/latest) and put it in the `plugins` or `mods` folder.

| Server | Minecraft | Jar |
| --- | --- | --- |
| Paper, Folia, Purpur, Leaf | 1.21 to 26.3 | `minecraft-discord-bridge-paper-<version>.jar` |
| Velocity 3.4 or newer, including 4.x | any | `minecraft-discord-bridge-velocity-<version>.jar` |
| Fabric | 26.1.2 to 26.3 | `minecraft-discord-bridge-fabric-<version>+26.1.2.jar` |
| Fabric | 1.21.11 | `minecraft-discord-bridge-fabric-<version>+1.21.11.jar` |
| Fabric | 1.21 and 1.21.1 | `minecraft-discord-bridge-fabric-<version>+1.21.1.jar` |
| Fabric | 1.20.1 | `minecraft-discord-bridge-fabric-<version>+1.20.1.jar` |
| NeoForge | 26.2 and 26.3 | `minecraft-discord-bridge-neoforge-<version>+26.2.jar` |
| NeoForge | 1.21 and 1.21.1 | `minecraft-discord-bridge-neoforge-<version>+1.21.1.jar` |
| Forge | 1.20.1 | `minecraft-discord-bridge-forge-<version>+1.20.1.jar` |

Fabric also needs [Fabric API](https://modrinth.com/mod/fabric-api). The Paper and Velocity jars need Java 21 or newer. Spigot, Bukkit, BungeeCord and Waterfall are not supported.

On a Velocity network, install the bridge on the proxy only. The proxy does not see deaths or advancements, and `/whitelist` does not work there.

Releases also go to Modrinth (every jar), CurseForge (Fabric, NeoForge and Forge) and Hangar (Paper and Velocity). Each GitHub release lists SHA-256 checksums in `SHA256SUMS`, and every jar has a build provenance attestation. To check a download:

```sh
gh attestation verify minecraft-discord-bridge-paper-<version>.jar --repo minodevss/minecraft-discord-bridge
```

## Quick start

Setup takes about two minutes.

1. Open the [Discord Developer Portal](https://discord.com/developers/applications) and click **New Application**. Open **Bot**, click **Reset Token** and copy the token.
2. Start the server once. The bridge creates its folder and an empty `secrets.yml` in it. Paste the token into `secrets.yml`:

   ```yaml
   discord-token: "your token"
   ```

   Or set the `MCDB_DISCORD_TOKEN` environment variable to the token, or `MCDB_DISCORD_TOKEN_FILE` to the path of a file that holds it. An environment variable takes priority over `secrets.yml`.
3. Run `/discord reload` in the server console. The bridge connects and logs an invite link with the permissions it needs. Open the link and add the bot to your Discord server.
4. In the Discord channel for the chat, run `/setup`.

By default, Discord shows `/setup` only to members with the **Manage Server** permission. The bot's owner, the account that owns the application in the Developer Portal, can run it directly. Anyone else also needs a one-time code: run `/discord setup` in the server console, then run `/setup code:<code>` in Discord within 10 minutes. To post events in a different channel, run `/setup as:events` there.

The bridge folder is `plugins/MinecraftDiscordBridge` on Paper, `plugins/minecraftdiscordbridge` on Velocity, and `config/minecraftdiscordbridge` on Fabric, NeoForge and Forge.

The bridge turns on the bot's **Message Content Intent** by itself, because it needs it to read Discord messages. If that fails, the log says so; turn it on under **Bot** in the Developer Portal.

## Commands

In Minecraft or the server console:

```
/discord                 Show the commands you can use
/discord link            Get a code to link your Discord account
/discord unlink          Unlink your Discord account
/discord toggle          Hide or show messages from Discord for yourself
/discord status          Show the Discord connection, queues and linked accounts
/discord reload          Reload config.yml and secrets.yml
/discord setup           Get a one-time code for /setup in Discord (console only)
```

| Permission | Default |
| --- | --- |
| `minecraftdiscordbridge.command.link` | everyone |
| `minecraftdiscordbridge.command.toggle` | everyone |
| `minecraftdiscordbridge.command.status` | operators |
| `minecraftdiscordbridge.command.reload` | operators |
| `minecraftdiscordbridge.command.setup` | operators |
| `minecraftdiscordbridge.gate.bypass` | operators |

On Fabric, NeoForge and Forge, a permissions mod such as LuckPerms can grant these nodes.

In Discord:

```
/setup [as] [code]                 Use this channel for chat or events
/list                              Show who is online
/link <code>                       Link your Discord account
/unlink                            Unlink your Discord account
/whitelist add|remove <player>     Change the whitelist (actions.whitelist)
/kick <player> [reason]            Kick a player (actions.kick)
```

`/link` and `/unlink` exist while linking is on. `/whitelist` and `/kick` exist only when you turn them on in `config.yml`, and by default only Discord administrators can use them. To let other roles use them, change the command permissions under **Server Settings > Integrations** in Discord.

## Configuration

The bridge folder holds three things:

- `config.yml`: every setting, each with a comment. Run `/discord reload` after you edit it. If the file has an error, the bridge keeps the old settings and the log says what to fix.
- `secrets.yml`: the bot token. The bridge creates it readable by the server's user only.
- `data/`: what the bridge saves by itself, such as the channels from `/setup` and the linked accounts.

| Section | What it controls |
| --- | --- |
| `language` | `en` or `ko` for the bridge's own messages |
| `discord` | Chat, events and console channel IDs. A channel ID in `config.yml` takes priority over `/setup`. |
| `chat` | Both directions, the in-game format in MiniMessage, avatars, `@name` pings for linked players, blocked words |
| `events` | Which events to post, and a role to ping when the server starts |
| `status` | The player count as the bot's status and in the channel topic |
| `linking` | Account linking, and whether players need a linked account or a Discord role to join |
| `role-sync` | Rules that pair Discord roles with permission groups |
| `actions` | `/whitelist` and `/kick` from Discord, off by default |
| `console` | The console mirror, and the Discord user IDs that may run console commands |

To copy a channel, role or user ID, turn on **Developer Mode** in Discord under **User Settings > Advanced**, then right-click the channel, role or user and click **Copy ID**.

Text from players and from Discord is never read as formatting codes, and the bridge never pings `@everyone`, `@here` or roles unless the config asks for it.

## Scope

The bridge connects one server or network to one Discord server, and each owner runs their own bot. It is not a moderation, ticket, music or leveling bot, and it does not sync bans, nicknames or inventories. It runs no console command from Discord unless `console.input-users` lists the Discord user.

## Build

```sh
./gradlew build                # core, paper, velocity and the Fabric 26.1.2 jar
./gradlew build -Pnodes=all    # every Fabric, NeoForge and Forge jar
```

The build needs JDK 25 and downloads JDK 17 and 21 for the older Minecraft versions. The jars are in `paper/build/libs`, `velocity/build/libs` and `modded/versions/<version>-<loader>/build/libs`.

## License

[Apache License 2.0](LICENSE). See [NOTICE](NOTICE). The jars bundle third-party libraries; [THIRD-PARTY-NOTICES](THIRD-PARTY-NOTICES) lists them with their licenses.
