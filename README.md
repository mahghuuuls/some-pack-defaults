# Some Pack Defaults

Some Pack Defaults lets a modpack set `server.properties` lines and gamerules from config files. The pack keeps the settings it needs in its own file, and each player or server admin gets one file for their own settings, filled from the pack's suggestions. Edit the file, restart, and the game follows.

- Player-facing description: [`MOD-PAGE.md`](MOD-PAGE.md)
- Release history: [`CHANGELOG.md`](CHANGELOG.md)
- License: MIT, see [`LICENSE`](LICENSE) and [`THIRD-PARTY-NOTICES.md`](THIRD-PARTY-NOTICES.md)

## Identity

- Mod ID `somepackdefaults`, Minecraft 1.12.2, Forge. No required mods.
- Runs on the logical server. Install it on dedicated servers, and in the client for singleplayer worlds. Clients without the mod can join a server that has it (`acceptableRemoteVersions = "*"`); the mod sends nothing to clients.

## Files

All in `config/somepackdefaults/`. Missing files are created at game or server launch; an existing file is never written.

| File | Owner | Read |
| --- | --- | --- |
| `pack.properties` | pack maker, shipped | at every server start and singleplayer world open |
| `suggestions.properties` | pack maker, shipped | only to make a missing `player.properties`: its lines are copied except lines starting with `##` |
| `player.properties` | player or server admin, never shipped | at every server start and singleplayer world open |

- Each non-comment line is one entry, `name=value`, read with the escape rules of `java.util.Properties`, so a line copied from `server.properties` means the same.
- A name on the vanilla 1.12.2 server-line list (or a host line) is a server line; every other name is a gamerule.
- When both files set the same name, the pack file's entry is used and the player's entry is reported as overridden. An invalid player entry is an error even when the pack file overrides it.
- Server lines are written to `server.properties` at every dedicated-server start, before vanilla reads the file, so they apply on that start. In singleplayer they are checked and reported, not used.
- Gamerules are set at the first server tick of every start and world open, in new and existing worlds alike. Mod-added gamerules are accepted when the world has them.
- `level-name` is allowed only in `player.properties` (and so in `suggestions.properties`). The host lines `server-ip`, `server-port`, `enable-rcon`, `rcon.port`, `rcon.password`, `enable-query`, `query.port` are refused in every file.
- `level-seed`, `level-type`, `generator-settings` and `generate-structures` affect only a world that is being made. `hardcore` affects an existing server at once. Vanilla applies `difficulty` to every world at every start.
- Removing an entry stops managing that setting; its current value stays.

## Errors

Every entry is checked before anything changes, except gamerules a world has only once it has loaded (and unknown names), which are checked at the first tick. On an invalid entry:

- Dedicated server, before the world loads (server lines and vanilla gamerules): the server stops with the errors in the log; `server.properties` is left as it was.
- Dedicated server, at the first tick (a gamerule a mod adds, or a name the world does not have, such as a typo): the start stops after the world loads; no gamerule is changed, but the valid server lines were already written to `server.properties` for that start.
- Singleplayer: about 3 seconds after the player joins, the world is stopped and the player sees the errors on Minecraft's disconnect screen; no gamerule is changed. Server-line value errors are reported in the log only; a misspelled server-line name is read as a gamerule and stops the world like any unknown gamerule.

Each error names the file, the line number, the entry, the problem and the allowed values.

## Inspection

`/somepackdefaults inspect` (permission level 2: operators, the server console, a singleplayer host with cheats on) lists every entry with its setting category, file and line, configured value, current value where it can be read, and status (applied, overridden by the pack file, not used in singleplayer, error), then every error. It reads only; it writes no file and changes no setting.

## Scope

- Only vanilla 1.12.2 server lines; lines added by other server software or mods are read as gamerule names and fail the check.
- The mod keeps no state of its own: no record in the server folder and no mark in a world.
- Packs must not ship `player.properties`: a launcher that updates the pack would replace the player's file.
- Overlaps: [Global GameRules](https://www.curseforge.com/minecraft/mc-mods/global-gamerules) also manages gamerules and writes in-game changes back to its own file; [Default World Generator (port)](https://www.curseforge.com/minecraft/mc-mods/default-world-generator-port) also sets the world type. Use one mod per setting. A hosting panel that rewrites `server.properties` before a start loses to the lines listed in these files.
