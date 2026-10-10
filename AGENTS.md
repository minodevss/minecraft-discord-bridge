# Minecraft Discord Bridge

Connects one Minecraft server (or one Velocity network) to one Discord server: chat both ways, game events, status, account linking and a few fixed actions.
Runs inside the server as a Paper or Velocity plugin, or a Fabric, NeoForge or Forge mod. Every owner runs their own bot.

## Commands
- `./gradlew build` builds api, core, paper, velocity and the active modded node.
- `./gradlew build -Pnodes=all` builds every modded node. Run it before finishing any change under `modded/` or `core/`.
- `./gradlew :core:test` while working on core.
- `./gradlew spotlessApply` before committing.
- All must pass before a commit. Never assume a failure is pre-existing.

## Architecture
The Gradle project graph is the architecture. Do not add edges.
- `api`: the public API for other plugins and mods. Own types only; never JDA, Adventure or Minecraft types.
- `core`: everything that does not need the game: Discord, config, storage, text, features. No `net.minecraft`, Bukkit, Paper, Velocity, Fabric or Forge imports. Ships with its libraries relocated as `core-all`.
- `paper`, `velocity`, `modded`: thin adapters. They implement core's platform interfaces and publish game events. No feature logic.
- `modded` is one Stonecutter tree. Loader code lives in `modded/fabric`, `modded/neoforge`, `modded/forge`; version differences use Stonecutter comments, never copies.

Threads:
- Game threads only hand events to core. Never block them and never do I/O on them.
- Core logic runs on the single core loop. Never block it: no `join`, `get`, `complete`, `sleep` or file/network calls.
- Blocking Discord work (login, shutdown) runs on the lifecycle thread. File I/O runs on the I/O executor.
- Game state is touched only through the platform scheduler.

Discord must never hurt the server: startup never waits for Discord, and shutdown finishes within 3 seconds.

## Code
- `api` and `core` target Java 17: records, sealed interfaces, switch expressions. Adapters use their Minecraft version's Java.
- Every package has `@NullMarked`; use `@Nullable` where null is allowed. NullAway enforces it.
- Small classes and methods; names say what they do. Prefer a better name or type over a comment.
- Parse input once into value types (`DiscordId`, `PlayerName`) and pass those inward.
- Text from players or Discord is data. Never parse it as MiniMessage or legacy codes; insert it with `Placeholder.unparsed` or `Placeholder.component`.
- Every Discord message sets allowed mentions explicitly.
- Logs are English, one line, and say how to fix the problem.
- Never log the bot token or webhook URLs.

## Comments
Default to none. Add one only for what code cannot say: a platform quirk, an outside constraint, a hidden invariant, or why the obvious way is wrong. One line, two at most.
- Good: `// NeoForge skips ServerStoppingEvent after a crash; ServerStoppedEvent always fires.`
- Bad: `// send the message` above `channel.send(message)`
- Javadoc only in `api`.
- No commented-out code. Never mention the chat, prompt or agent.

## Tests
- Tests are the spec; name them as behavior: `escapesHeadingAtLineStart`.
- Core logic gets unit tests with fakes. Discord REST is tested against MockWebServer. No real network in tests.
- Bug fix: write the failing test first.

## Dependencies
- Prefer the JDK and existing deps. A new dep needs a one-line reason in the commit.
- Versions live in `gradle/libs.versions.toml`, or `modded/stonecutter.properties.toml` for per-version loader deps.

## Commits
- Conventional Commits with the module as scope: `fix(core): escape list markers at line start`.
- One logical change per commit.

## Never
- Feature logic in an adapter.
- Blocking a game thread or the core loop.
- Raw console input from Discord unless the config lists Discord user IDs.
- New config keys or commands without an approved issue.
- Unrelated edits, or skipping a failing test.
