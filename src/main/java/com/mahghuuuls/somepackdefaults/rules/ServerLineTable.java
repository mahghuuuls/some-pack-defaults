package com.mahghuuuls.somepackdefaults.rules;

import com.mahghuuuls.somepackdefaults.config.NameKinds;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The vanilla 1.12.2 dedicated-server lines: which ones a pack may set, what each accepts, and
 * which ones belong to the server host. Ranges follow how {@code DedicatedServer},
 * {@code DedicatedPlayerList}, and {@code PlayerChunkMap} read each line, so a value passes only
 * when the server uses it as written. {@code op-permission-level} is limited to 1 to 4, the levels
 * the game defines. This table is the authoritative list of pack lines and their values, and so
 * of which names are server lines; every other name is a gamerule.
 */
public final class ServerLineTable implements NameKinds {

    /** The table as the files' {@link NameKinds}. */
    public static final ServerLineTable NAME_KINDS = new ServerLineTable();

    /** The line whose value is a world type; its existence is checked once every mod has registered its types. */
    public static final String LEVEL_TYPE = "level-type";
    /** The line that names the world folder; only the player file may set it. */
    public static final String LEVEL_NAME = "level-name";

    private static final List<String> HOST_LINES = Collections.unmodifiableList(Arrays.asList(
            "server-ip", "server-port", "enable-rcon", "rcon.port", "rcon.password", "enable-query", "query.port"));

    private static final Map<String, ValueRule> PACK_LINES;

    static {
        Map<String, ValueRule> lines = new LinkedHashMap<String, ValueRule>();
        lines.put("allow-flight", ValueRule.bool());
        lines.put("allow-nether", ValueRule.bool());
        lines.put("broadcast-console-to-ops", ValueRule.bool());
        lines.put("broadcast-rcon-to-ops", ValueRule.bool());
        lines.put("difficulty", ValueRule.integer(0, 3));
        lines.put("enable-command-block", ValueRule.bool());
        lines.put("force-gamemode", ValueRule.bool());
        lines.put("gamemode", ValueRule.integer(0, 3));
        lines.put("generate-structures", ValueRule.bool());
        lines.put("generator-settings", ValueRule.anyText());
        lines.put("hardcore", ValueRule.bool());
        lines.put(LEVEL_NAME, ValueRule.nonEmptyText());
        lines.put("level-seed", ValueRule.anyText());
        lines.put(LEVEL_TYPE, ValueRule.nonEmptyText());
        lines.put("max-build-height", ValueRule.integer(64, 256, 16));
        lines.put("max-players", ValueRule.anyInteger());
        lines.put("max-tick-time", ValueRule.anyLong());
        lines.put("max-world-size", ValueRule.integer(1, 29999984));
        lines.put("motd", ValueRule.anyText());
        lines.put("network-compression-threshold", ValueRule.anyInteger());
        lines.put("online-mode", ValueRule.bool());
        lines.put("op-permission-level", ValueRule.integer(1, 4));
        lines.put("player-idle-timeout", ValueRule.anyInteger());
        lines.put("prevent-proxy-connections", ValueRule.bool());
        lines.put("pvp", ValueRule.bool());
        lines.put("resource-pack", ValueRule.anyText());
        lines.put("resource-pack-sha1", ValueRule.anyText());
        lines.put("resource-pack-hash", ValueRule.anyText());
        lines.put("snooper-enabled", ValueRule.bool());
        lines.put("spawn-animals", ValueRule.bool());
        lines.put("spawn-monsters", ValueRule.bool());
        lines.put("spawn-npcs", ValueRule.bool());
        lines.put("spawn-protection", ValueRule.anyInteger());
        lines.put("use-native-transport", ValueRule.bool());
        lines.put("view-distance", ValueRule.integer(3, 32));
        lines.put("white-list", ValueRule.bool());
        PACK_LINES = Collections.unmodifiableMap(lines);
    }

    private ServerLineTable() {
    }

    /** Host lines count as server lines, so an entry for one is refused as a host line, not as an unknown gamerule. */
    @Override
    public boolean isServerLine(String name) {
        return isHostLine(name) || isPackLine(name);
    }

    public static boolean isHostLine(String name) {
        return HOST_LINES.contains(name);
    }

    /** The host lines in a fixed order, for messages. */
    public static List<String> hostLineNames() {
        return HOST_LINES;
    }

    public static boolean isPackLine(String name) {
        return PACK_LINES.containsKey(name);
    }

    public static Set<String> packLineNames() {
        return PACK_LINES.keySet();
    }

    /** Null when the value passes, otherwise what is wrong. Only for pack lines. */
    static String check(String name, String value) {
        return PACK_LINES.get(name).check(value);
    }

    static String allowed(String name) {
        return PACK_LINES.get(name).allowed();
    }
}
