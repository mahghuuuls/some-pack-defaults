package com.mahghuuuls.somepackdefaults.rules;

import com.mahghuuuls.somepackdefaults.config.Entry;
import com.mahghuuuls.somepackdefaults.config.EntrySet;
import com.mahghuuuls.somepackdefaults.config.MalformedLine;
import com.mahghuuuls.somepackdefaults.config.SettingCategory;
import com.mahghuuuls.somepackdefaults.config.Source;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntryCheckerTest {

    private static Entry server(Source source, String name, String value, int line) {
        return new Entry(SettingCategory.SERVER_LINE, source, name, value, line);
    }

    private static List<Problem> check(Entry... entries) {
        return EntryChecker.checkServerLines(new EntrySet(Arrays.asList(entries), Collections.<MalformedLine>emptyList()));
    }

    @Test
    void validEntriesHaveNoProblem() {
        assertTrue(check(server(Source.PACK, "pvp", "false", 1), server(Source.PLAYER, "motd", "Pack", 1)).isEmpty());
    }

    @Test
    void valueProblemNamesFileLineEntryProblemAndAllowedValues() {
        List<Problem> problems = check(server(Source.PLAYER, "gamemode", "9", 4));
        assertEquals(1, problems.size());
        assertEquals("player.properties line 4: gamemode=9: out of range. Allowed: 0 to 3.", problems.get(0).message());
    }

    @Test
    void hostLineIsRefusedInEveryFile() {
        assertEquals("player.properties line 2: server-port=25570: server-port is a host line, which belongs to the server admin "
                + "and is not set by this mod. Allowed: a vanilla 1.12.2 server line other than the host lines server-ip, server-port, enable-rcon, rcon.port, rcon.password, enable-query, query.port.",
                check(server(Source.PLAYER, "server-port", "25570", 2)).get(0).message());
        assertEquals(1, check(server(Source.PACK, "rcon.password", "x", 1)).size());
    }

    @Test
    void levelNameIsRefusedInThePackFileOnly() {
        List<Problem> problems = check(server(Source.PACK, "level-name", "packworld", 3));
        assertEquals(1, problems.size());
        assertEquals("pack.properties line 3: level-name=packworld: level-name belongs in player.properties (the pack can suggest it in "
                + "suggestions.properties). Allowed: every server line except level-name and the host lines.", problems.get(0).message());
        assertTrue(check(server(Source.PLAYER, "level-name", "packworld", 3)).isEmpty());
    }

    @Test
    void emptyLevelNameInThePlayerFileIsRefused() {
        assertEquals(1, check(server(Source.PLAYER, "level-name", "", 1)).size());
    }

    @Test
    void theSameNameInBothFilesIsNoProblem() {
        assertTrue(check(server(Source.PACK, "pvp", "false", 3), server(Source.PLAYER, "pvp", "true", 5)).isEmpty());
    }

    @Test
    void nameTwiceInOneFileIsReportedWithBothLines() {
        List<Problem> problems = check(server(Source.PLAYER, "pvp", "false", 1), server(Source.PLAYER, "pvp", "true", 2));
        assertEquals(1, problems.size());
        assertEquals("player.properties line 2: pvp=true: pvp appears twice in this file (first at line 1). Allowed: each name once per file.",
                problems.get(0).message());
    }

    @Test
    void malformedLinesAreReportedByTheLineCheckOnly() {
        EntrySet set = new EntrySet(Collections.<Entry>emptyList(),
                Collections.singletonList(new MalformedLine(Source.PACK, 7, "=false", "not a name=value line")));
        assertTrue(EntryChecker.checkServerLines(set).isEmpty());
        assertTrue(EntryChecker.checkGamerulesBeforeLoad(set, VANILLA_RULES).isEmpty());
        List<Problem> problems = EntryChecker.checkLines(set);
        assertEquals("pack.properties line 7: =false: not a name=value line. Allowed: name=value, such as pvp=false or keepInventory=true.",
                problems.get(0).message());
        assertEquals(1, EntryChecker.checkGamerules(set, VANILLA_RULES).size());
    }

    private static final WorldTypeLookup VANILLA_AND_RTG = new WorldTypeLookup() {
        @Override
        public boolean isKnown(String name) {
            return knownNames().contains(name.toLowerCase(java.util.Locale.ROOT));
        }

        @Override
        public List<String> knownNames() {
            return Arrays.asList("default", "flat", "largebiomes", "amplified", "rtg");
        }
    };

    @Test
    void worldTypeKnownToTheGamePasses() {
        assertEquals(null, EntryChecker.checkWorldType(server(Source.PLAYER, "level-type", "RTG", 3), VANILLA_AND_RTG));
    }

    @Test
    void unknownWorldTypeNamesTheRegisteredTypes() {
        Problem problem = EntryChecker.checkWorldType(server(Source.PLAYER, "level-type", "NOSUCH", 3), VANILLA_AND_RTG);
        assertEquals("player.properties line 3: level-type=NOSUCH: no installed mod or Minecraft itself has this world type. "
                + "Allowed: a registered world type: default, flat, largebiomes, amplified, rtg.", problem.message());
    }

    private static final GameruleLookup VANILLA_RULES = new GameruleLookup() {
        @Override
        public Kind kindOf(String name) {
            if (name.equals("keepInventory") || name.equals("doDaylightCycle")) {
                return Kind.TRUE_OR_FALSE;
            }
            return name.equals("randomTickSpeed") ? Kind.WHOLE_NUMBER : null;
        }
    };

    private static Entry gamerule(Source source, String name, String value, int line) {
        return new Entry(SettingCategory.GAMERULE, source, name, value, line);
    }

    @Test
    void gameruleValuesFollowTheGameruleCommand() {
        List<Problem> problems = EntryChecker.checkGamerules(new EntrySet(Arrays.asList(
                gamerule(Source.PACK, "keepInventory", "true", 1),
                gamerule(Source.PACK, "doDaylightCycle", "False", 2),
                gamerule(Source.PACK, "randomTickSpeed", "fast", 3)), Collections.<MalformedLine>emptyList()), VANILLA_RULES);
        assertEquals(2, problems.size());
        assertEquals("pack.properties line 2: doDaylightCycle=False: doDaylightCycle takes true or false. Allowed: true or false.",
                problems.get(0).message());
        assertEquals("pack.properties line 3: randomTickSpeed=fast: randomTickSpeed takes a whole number. Allowed: a whole number.",
                problems.get(1).message());
    }

    @Test
    void aNameFromAnotherModsServerLinesIsAnUnknownGameruleThatSaysOnlyVanillaLinesAreSupported() {
        List<Problem> problems = EntryChecker.checkGamerules(new EntrySet(Collections.singletonList(
                gamerule(Source.PACK, "defaultworldgenerator-port", "x", 1)), Collections.<MalformedLine>emptyList()), VANILLA_RULES);
        assertEquals("pack.properties line 1: defaultworldgenerator-port=x: this world has no gamerule named defaultworldgenerator-port "
                + "(a typo, or the mod that adds it is not installed). Allowed: a gamerule this world has (/gamerule lists them), "
                + "or a vanilla 1.12.2 server line; only vanilla server lines are supported.", problems.get(0).message());
    }

    @Test
    void beforeLoadOnlyVanillaNamesAreCheckedForValues() {
        EntrySet set = new EntrySet(Arrays.asList(gamerule(Source.PLAYER, "modRule", "anything", 1), gamerule(Source.PLAYER, "keepInventory", "yes", 2)),
                Collections.<MalformedLine>emptyList());
        List<Problem> problems = EntryChecker.checkGamerulesBeforeLoad(set, VANILLA_RULES);
        assertEquals(1, problems.size());
        assertEquals(2, problems.get(0).lineNumber());
        assertEquals(2, EntryChecker.checkGamerules(set, VANILLA_RULES).size());
    }

    @Test
    void aGameruleInBothFilesIsNoProblemButTwiceInOneFileIs() {
        EntrySet both = new EntrySet(Arrays.asList(gamerule(Source.PACK, "keepInventory", "true", 1), gamerule(Source.PLAYER, "keepInventory", "false", 1)),
                Collections.<MalformedLine>emptyList());
        assertTrue(EntryChecker.checkGamerulesBeforeLoad(both, VANILLA_RULES).isEmpty());
        EntrySet twice = new EntrySet(Arrays.asList(gamerule(Source.PACK, "keepInventory", "true", 1), gamerule(Source.PACK, "keepInventory", "true", 4)),
                Collections.<MalformedLine>emptyList());
        assertEquals(1, EntryChecker.checkGamerulesBeforeLoad(twice, VANILLA_RULES).size());
    }

    @Test
    void gameruleEntriesAreNotPartOfTheServerLineCheck() {
        Entry gamerule = new Entry(SettingCategory.GAMERULE, Source.PACK, "pvp", "banana", 1);
        assertTrue(EntryChecker.checkServerLines(new EntrySet(Collections.singletonList(gamerule), Collections.<MalformedLine>emptyList())).isEmpty());
    }
}
