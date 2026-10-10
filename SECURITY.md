# Security

Please report vulnerabilities privately through [GitHub security advisories](https://github.com/minodevss/minecraft-discord-bridge/security/advisories/new).
Do not open a public issue for security problems.

Only the latest release receives fixes.

## In scope

- Bot token handling: the token or a webhook URL appears in a log, the console channel, a Discord message or a world-readable file.
- Console access: someone not listed in `console.input-users` runs a console command from Discord, or the console channel shows secrets, IP addresses or passwords that the bridge should hide.
- Mention abuse: a player or Discord user makes the bridge ping `@everyone`, `@here`, a role or a user that the config does not allow.
- Formatting injection: text from players or Discord is read as MiniMessage, legacy color codes or Discord markdown where it should be plain text.
- Setup and linking codes that can be guessed, reused or brute-forced, and Discord commands that run without the permission they require.
- Anything from Discord that can block, crash or slow the Minecraft server.

## Out of scope

- A leaked token or a compromised Discord account, server or host.
- Discord roles or permissions that the server owner gave out.
- Bugs in Discord, Minecraft, the mod loader or the plugin platform.

## Verifying releases

Each release has a `SHA256SUMS` file and a build provenance attestation for every jar:

```sh
gh attestation verify minecraft-discord-bridge-paper-<version>.jar --repo minodevss/minecraft-discord-bridge
```
