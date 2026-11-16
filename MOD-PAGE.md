A modpack can ship mods and their config files, but it cannot set single lines of `server.properties` or the gamerules of a world. Usually a pack maker asks server admins and players to set them by hand, or ships a whole `server.properties` that replaces the host's own values. This mod lets the pack carry those settings, and keeps each setting in one file.

The pack keeps the settings it needs in its own file. Each player or server admin has one file of their own for the settings that are a matter of taste. The mod makes it once from the pack's suggestions. Every gamerule line in both files is set at every server start and every singleplayer world open; every server line is written to `server.properties` at every dedicated-server start. A changed line takes effect when the server restarts or the world is opened again.

<span style="color:#d6a100">**AI usage disclaimer:** This mod was developed with AI-agent assistance using [this agent workflow](https://github.com/mahghuuuls/minecraft-1.12.2-mod-agent-workflow). The project owner reviewed the work during development.</span>

## Examples

- A pack with jetpacks or flying mounts puts `allow-flight=true` in `pack.properties`. Every server that runs the pack lets players fly instead of kicking them, and the admin does not have to change `server.properties` by hand.
- A pack where time should not pass puts `doDaylightCycle=false` in `pack.properties`. Every world gets it, new and old, in singleplayer and on servers; time stops where it is in each world.
- A pack suggests `keepInventory=true`. A player who wants to lose items on death sets `keepInventory=false` in `player.properties` once, and it holds in every world they open, also after a pack update changes the suggestions.
- A server admin raises `view-distance` in `player.properties`, not in `server.properties`, where the mod would set it back at the next start. A pack update that changes the suggestions does not undo it.

## The files

All three are in `config/somepackdefaults/`. Each line is `name=value`: server lines as in `server.properties`, gamerules by their name. Lines starting with `#` do nothing. When the pack ships none of these files, the mod creates each with only such example lines, so it changes nothing until a `#` is removed.

- **`pack.properties`**: the pack maker's settings, shipped with the pack. Players do not edit it. A line here wins over the same line in `player.properties`.
- **`suggestions.properties`**: shipped with the pack. When a player has no `player.properties` yet, the mod makes it from this file. Lines starting with `##` are notes for the pack maker and are not copied; lines starting with one `#` are copied as examples the player can turn on.
- **`player.properties`**: the player's or server admin's own settings. The mod and pack updates never change it.

**pack.properties**

```properties
allow-flight=true
doDaylightCycle=false
```

**suggestions.properties**

```properties
view-distance=10
keepInventory=true
```

## Good to know

- Never ship `player.properties`: a pack update would replace the player's settings. The mod creates one in your own instance too, so leave it out of the export.
- When a pack update adds the mod with active suggestions, every player and server gets a `player.properties` at the next launch, and its lines then apply to existing servers and worlds as well; an active `level-name` there moves existing servers to that world folder. A `/gamerule` change to a gamerule in these files lasts until the next start or world open.
- Removing a line stops the mod from setting it; the current value stays.
- Only vanilla server lines are accepted. `server-ip`, `server-port` and the rcon and query lines stay in `server.properties`; `level-name` goes in the suggestions, not in `pack.properties`. Every other name is read as a gamerule, including gamerules other mods add.
- `level-seed`, `level-type`, `generator-settings` and `generate-structures` change only a world that is being made. `hardcore=true` applies to an existing server at once: a player who dies is banned.
- A line with a mistake stops the start: on a server the errors are in the log; in singleplayer the world closes a few seconds after you join and shows them. Each error names the file, the line and the allowed values. In singleplayer a server line with a bad value is only logged, but a misspelled name counts as an unknown gamerule and stops the world.
- `/somepackdefaults inspect` lists every line with its file, its value, and whether it was applied, overridden by `pack.properties`, not used in singleplayer, or an error. Operators, the server console and singleplayer players with cheats on can run it.
- [Global GameRules](https://www.curseforge.com/minecraft/mc-mods/global-gamerules) also sets gamerules and [Default World Generator (port)](https://www.curseforge.com/minecraft/mc-mods/default-world-generator-port) also sets the world type; with either installed, set each setting in one mod only. A hosting panel that rewrites `server.properties` before a start loses to the lines in these files.

Install it on the dedicated server, and on the client for singleplayer. Players who join a server do not need it.

[Full reference, source and issues on GitHub](https://github.com/mahghuuuls/some-pack-defaults)
