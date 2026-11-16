package com.mahghuuuls.somepackdefaults.rules;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every row of the pack-line table, as vanilla 1.12.2 reads the line: a value vanilla uses as
 * written is accepted, a value it would change is refused. The names are the keys read in
 * DedicatedServer, DedicatedPlayerList, and the old resource-pack-hash conversion.
 */
class ServerLineTableTest {

    private static final String[] BOOLEAN_LINES = {
        "allow-flight", "allow-nether", "broadcast-console-to-ops", "broadcast-rcon-to-ops", "enable-command-block", "force-gamemode",
        "generate-structures", "hardcore", "online-mode", "prevent-proxy-connections", "pvp", "snooper-enabled", "spawn-animals",
        "spawn-monsters", "spawn-npcs", "use-native-transport", "white-list"};
    private static final String[] ANY_TEXT_LINES = {"generator-settings", "level-seed", "motd", "resource-pack", "resource-pack-sha1", "resource-pack-hash"};
    private static final String[] NON_EMPTY_TEXT_LINES = {"level-name", ServerLineTable.LEVEL_TYPE};
    private static final String[] ANY_INTEGER_LINES = {"max-players", "network-compression-threshold", "player-idle-timeout", "spawn-protection"};

    private static void accepts(String name, String value) {
        assertNull(ServerLineTable.check(name, value), name + "=" + value);
    }

    private static void refuses(String name, String value) {
        assertNotNull(ServerLineTable.check(name, value), name + "=" + value);
    }

    @Test
    void namesAreExactlyTheVanillaPackLines() {
        Set<String> expected = new HashSet<String>();
        expected.addAll(Arrays.asList(BOOLEAN_LINES));
        expected.addAll(Arrays.asList(ANY_TEXT_LINES));
        expected.addAll(Arrays.asList(NON_EMPTY_TEXT_LINES));
        expected.addAll(Arrays.asList(ANY_INTEGER_LINES));
        expected.addAll(Arrays.asList("difficulty", "gamemode", "max-build-height", "max-tick-time", "max-world-size", "op-permission-level", "view-distance"));
        assertEquals(expected, ServerLineTable.packLineNames());
    }

    @Test
    void hostLinesAreNotPackLines() {
        for (String host : new String[] {"server-ip", "server-port", "enable-rcon", "rcon.port", "rcon.password", "enable-query", "query.port"}) {
            assertTrue(ServerLineTable.isHostLine(host), host);
            assertFalse(ServerLineTable.isPackLine(host), host);
        }
        assertFalse(ServerLineTable.isPackLine("defaultworldgenerator-port"));
        assertFalse(ServerLineTable.isPackLine("announce-player-achievements"));
    }

    @Test
    void packLinesAndHostLinesAreServerLinesAndEveryOtherNameIsAGamerule() {
        for (String name : ServerLineTable.packLineNames()) {
            assertTrue(ServerLineTable.NAME_KINDS.isServerLine(name), name);
        }
        for (String host : ServerLineTable.hostLineNames()) {
            assertTrue(ServerLineTable.NAME_KINDS.isServerLine(host), host);
        }
        for (String other : new String[] {"keepInventory", "doDaylightCycle", "defaultworldgenerator-port", "Pvp"}) {
            assertFalse(ServerLineTable.NAME_KINDS.isServerLine(other), other);
        }
    }

    @Test
    void everyBooleanLineTakesTrueOrFalseOnly() {
        for (String name : BOOLEAN_LINES) {
            accepts(name, "true");
            accepts(name, "FALSE");
            refuses(name, "yes");
            refuses(name, "true ");
            refuses(name, "");
            assertEquals("true or false", ServerLineTable.allowed(name), name);
        }
    }

    @Test
    void everyTextLine() {
        for (String name : ANY_TEXT_LINES) {
            accepts(name, "");
            accepts(name, "anything at all");
        }
        // Vanilla only warns about a resource-pack-sha1 that is not a hash and uses it as written.
        accepts("resource-pack-sha1", "abc");
        for (String name : NON_EMPTY_TEXT_LINES) {
            refuses(name, "");
            accepts(name, "RTG");
        }
    }

    @Test
    void everyUnboundedNumberLine() {
        for (String name : ANY_INTEGER_LINES) {
            accepts(name, "-5");
            accepts(name, "0");
            accepts(name, "2147483647");
            refuses(name, "2147483648");
            refuses(name, "ten");
            refuses(name, "5 ");
        }
        accepts("max-tick-time", "-1");
        accepts("max-tick-time", "60000");
        accepts("max-tick-time", "9223372036854775807");
        refuses("max-tick-time", "1m");
    }

    @Test
    void difficultyAndGamemodeAreZeroToThree() {
        refuses("difficulty", "-1");
        accepts("difficulty", "0");
        accepts("difficulty", "3");
        refuses("difficulty", "4");
        refuses("difficulty", "hardd");
        refuses("gamemode", "-1");
        accepts("gamemode", "0");
        accepts("gamemode", "3");
        refuses("gamemode", "4");
        refuses("gamemode", "survival");
        assertEquals("0 to 3", ServerLineTable.allowed("gamemode"));
    }

    @Test
    void buildHeightIsAMultipleOfSixteenFrom64To256() {
        refuses("max-build-height", "48");
        accepts("max-build-height", "64");
        refuses("max-build-height", "200");
        accepts("max-build-height", "208");
        accepts("max-build-height", "256");
        refuses("max-build-height", "272");
        assertEquals("64 to 256, in steps of 16", ServerLineTable.allowed("max-build-height"));
    }

    @Test
    void otherClampedNumbers() {
        refuses("view-distance", "2");
        accepts("view-distance", "3");
        accepts("view-distance", "32");
        refuses("view-distance", "33");
        refuses("max-world-size", "0");
        accepts("max-world-size", "1");
        accepts("max-world-size", "29999984");
        refuses("max-world-size", "29999985");
        refuses("op-permission-level", "0");
        accepts("op-permission-level", "1");
        accepts("op-permission-level", "4");
        refuses("op-permission-level", "5");
    }
}
