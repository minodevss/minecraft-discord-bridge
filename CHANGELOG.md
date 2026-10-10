# Changelog

All notable changes to this project are documented in this file.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project uses [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added
- Jars for Paper 1.21 to 26.3 (also Folia, Purpur and Leaf), Velocity 3.4 and newer, Fabric 26.1.2 to 26.3, 1.21.11, 1.21 to 1.21.1 and 1.20.1, NeoForge 26.2 to 26.3 and 1.21 to 1.21.1, and Forge 1.20.1.
- Chat both ways between Minecraft and one Discord channel, with player names and heads on Discord and replies, attachments, stickers and polls shown in game.
- Joins, leaves, deaths, advancements and server start, stop and crash posts, in the chat channel or a separate events channel, with an optional role ping on start.
- The player count as the bot's status or the chat channel's topic.
- Channel setup with `/setup` in Discord, for the bot owner or with a one-time code from the server console.
- Account linking with `/discord link` and `/link`, and optional pings of linked players with `@name` in game.
- `/list` in Discord, and optional `/whitelist` and `/kick`.
- A console channel that mirrors the server console and hides tokens, webhook URLs, IP addresses and passwords.
- `/discord toggle`, `/discord status`, `/discord reload` and `/discord setup` in game.
- `config.yml` with a comment for every setting, checked on load and reload; the bot token in `secrets.yml`, `MCDB_DISCORD_TOKEN` or `MCDB_DISCORD_TOKEN_FILE`.
- English and Korean texts.
- The server starts without waiting for Discord, and the bridge stops within a few seconds.

[Unreleased]: https://github.com/minodevss/minecraft-discord-bridge/commits/main
