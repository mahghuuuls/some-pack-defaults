# Changelog

## 1.0.0

### Added

- `config/somepackdefaults/pack.properties`: the pack's server lines and gamerules, set at every server start and every singleplayer world open. A line here wins over the same line in the player file.
- `config/somepackdefaults/suggestions.properties`: the pack's suggestions; a missing `player.properties` is made from them once, without the `##` note lines.
- `config/somepackdefaults/player.properties`: the player's or server admin's own server lines and gamerules, set at every start or world open; never changed by the mod or by pack updates.
- Checks of every line before anything changes. An invalid line stops the dedicated server before the world loads, or stops the world after it loads (a gamerule another mod adds, or any gamerule in singleplayer), with a message naming the file, the line, the entry and the allowed values. In singleplayer the player sees the message and returns to the menu.
- `/somepackdefaults inspect` (permission level 2): every entry with its setting category, file, configured value, current value and status. It changes nothing.
- A start summary in the log for each server start and world open.
