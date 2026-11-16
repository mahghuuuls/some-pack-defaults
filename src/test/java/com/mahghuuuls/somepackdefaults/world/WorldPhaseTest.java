package com.mahghuuuls.somepackdefaults.world;

import com.mahghuuuls.somepackdefaults.config.Entry;
import com.mahghuuuls.somepackdefaults.config.EntrySet;
import com.mahghuuuls.somepackdefaults.config.MalformedLine;
import com.mahghuuuls.somepackdefaults.config.SettingCategory;
import com.mahghuuuls.somepackdefaults.config.Source;
import com.mahghuuuls.somepackdefaults.report.StartBlocker;
import com.mahghuuuls.somepackdefaults.report.StartReport;
import com.mahghuuuls.somepackdefaults.rules.Problem;
import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The first-tick step against a world whose gamerules are a map, as a loaded world would hold them. */
class WorldPhaseTest {

    private static final class RecordingBlocker implements StartBlocker {
        List<Problem> beforeLoad;
        List<Problem> afterLoad;

        @Override
        public void stopBeforeWorldLoad(List<Problem> problems) {
            beforeLoad = problems;
        }

        @Override
        public void stopAfterWorldLoad(List<Problem> problems) {
            afterLoad = problems;
        }
    }

    /** A loaded world: vanilla-like rules plus one rule a mod added as text after a reload. */
    private static final class MapWorld implements GameruleTarget {
        final Map<String, String> values = new LinkedHashMap<String, String>();
        final Map<String, Kind> kinds = new LinkedHashMap<String, Kind>();
        final List<String> setOrder = new ArrayList<String>();

        MapWorld() {
            rule("doDaylightCycle", "true", Kind.TRUE_OR_FALSE);
            rule("keepInventory", "false", Kind.TRUE_OR_FALSE);
            rule("randomTickSpeed", "3", Kind.WHOLE_NUMBER);
            rule("doMobSpawning", "true", Kind.TRUE_OR_FALSE);
            rule("modFeature", "on", Kind.TEXT);
        }

        void rule(String name, String value, Kind kind) {
            values.put(name, value);
            kinds.put(name, kind);
        }

        @Override
        public Kind kindOf(String name) {
            return kinds.get(name);
        }

        @Override
        public void set(String name, String value) {
            if (!values.containsKey(name)) {
                throw new AssertionError("created a gamerule: " + name);
            }
            values.put(name, value);
            setOrder.add(name);
        }
    }

    private RecordingBlocker blocker;
    private WorldPhase phase;
    private MapWorld world;

    @BeforeEach
    void setUp() {
        blocker = new RecordingBlocker();
        phase = new WorldPhase(blocker, LogManager.getLogger("test"));
        world = new MapWorld();
    }

    private static Entry rule(Source source, String name, String value, int line) {
        return new Entry(SettingCategory.GAMERULE, source, name, value, line);
    }

    private static EntrySet set(Entry... entries) {
        return new EntrySet(Arrays.asList(entries), Collections.<MalformedLine>emptyList());
    }

    @Test
    void setsTheGamerulesOfBothFilesAndNothingElse() {
        phase.firstTick(set(rule(Source.PACK, "doDaylightCycle", "false", 1), rule(Source.PLAYER, "modFeature", "off", 2)), world);
        assertNull(blocker.afterLoad);
        assertEquals("false", world.values.get("doDaylightCycle"));
        assertEquals("off", world.values.get("modFeature"));
        assertEquals("false", world.values.get("keepInventory"));
        assertEquals("3", world.values.get("randomTickSpeed"));
        assertEquals("gamerules: 2 applied, 0 overridden by the pack file, 0 not applied, 0 errors",
                phase.report().summaryLines(SettingCategory.GAMERULE).get(0));
    }

    @Test
    void everyOpenSetsTheEntriesAgainSoAGameruleChangeLastsUntilTheNextOpen() {
        EntrySet entries = set(rule(Source.PLAYER, "keepInventory", "true", 1));
        phase.firstTick(entries, world);
        world.values.put("keepInventory", "false");
        phase.firstTick(entries, world);
        assertEquals("true", world.values.get("keepInventory"));
    }

    @Test
    void thePackFileWinsAndThePlayerGameruleIsReportedAsOverridden() {
        phase.firstTick(set(rule(Source.PACK, "doDaylightCycle", "false", 1), rule(Source.PLAYER, "doDaylightCycle", "true", 3),
                rule(Source.PLAYER, "keepInventory", "true", 4)), world);
        assertNull(blocker.afterLoad);
        assertEquals("false", world.values.get("doDaylightCycle"));
        assertEquals("true", world.values.get("keepInventory"));
        assertEquals(Arrays.asList("doDaylightCycle", "keepInventory"), world.setOrder);
        assertTrue(phase.report().summaryLines(SettingCategory.GAMERULE)
                .contains("  gamerule doDaylightCycle=true (player.properties line 3): overridden by the pack file"),
                phase.report().summaryLines(SettingCategory.GAMERULE).toString());
    }

    @Test
    void anInvalidOverriddenPlayerGameruleStillStops() {
        phase.firstTick(set(rule(Source.PACK, "keepInventory", "true", 1), rule(Source.PLAYER, "keepInventory", "yes", 2)), world);
        assertEquals(1, blocker.afterLoad.size());
        assertTrue(blocker.afterLoad.get(0).message().startsWith("player.properties line 2: keepInventory=yes"), blocker.afterLoad.get(0).message());
        assertEquals("false", world.values.get("keepInventory"));
    }

    @Test
    void anInvalidValueStopsAndChangesNothing() {
        phase.firstTick(set(rule(Source.PACK, "doDaylightCycle", "false", 1), rule(Source.PACK, "keepInventory", "yes", 2)), world);
        assertEquals(1, blocker.afterLoad.size());
        assertEquals("pack.properties line 2: keepInventory=yes: keepInventory takes true or false. Allowed: true or false.",
                blocker.afterLoad.get(0).message());
        assertEquals("true", world.values.get("doDaylightCycle"));
    }

    @Test
    void aMissingGameruleStopsAndIsNeverCreated() {
        phase.firstTick(set(rule(Source.PLAYER, "keepInventroy", "true", 1)), world);
        assertEquals(1, blocker.afterLoad.size());
        assertTrue(blocker.afterLoad.get(0).message().contains("this world has no gamerule named keepInventroy"), blocker.afterLoad.get(0).message());
        assertTrue(blocker.afterLoad.get(0).message().contains("only vanilla server lines are supported"), blocker.afterLoad.get(0).message());
        assertFalse(world.values.containsKey("keepInventroy"));
    }

    @Test
    void aMalformedLineStopsTheWorld() {
        EntrySet entries = new EntrySet(Collections.<Entry>emptyList(),
                Collections.singletonList(new MalformedLine(Source.PLAYER, 3, "=true", "not a name=value line")));
        phase.firstTick(entries, world);
        assertEquals(1, blocker.afterLoad.size());
        assertTrue(blocker.afterLoad.get(0).message().startsWith("player.properties line 3: =true: not a name=value line"), blocker.afterLoad.get(0).message());
    }

    @Test
    void singleplayerReportsServerLinesAsUnusedAndTheirErrorsWithoutStopping() {
        Entry difficulty = new Entry(SettingCategory.SERVER_LINE, Source.PLAYER, "difficulty", "hardd", 1);
        Entry motd = new Entry(SettingCategory.SERVER_LINE, Source.PACK, "motd", "Pack", 1);
        EntrySet entries = set(motd, difficulty, rule(Source.PLAYER, "doDaylightCycle", "false", 2));
        phase.reportServerLinesInSingleplayer(entries);
        phase.firstTick(entries, world);
        assertNull(blocker.afterLoad, "a server-line error does not stop a singleplayer world");
        assertEquals("false", world.values.get("doDaylightCycle"));
        assertEquals(1, phase.report().problems(SettingCategory.SERVER_LINE).size());
        assertTrue(phase.report().problems(SettingCategory.SERVER_LINE).get(0).message().startsWith("player.properties line 1: difficulty=hardd"));
        List<StartReport.Status> statuses = new ArrayList<StartReport.Status>();
        for (StartReport.Status status : phase.report().statuses()) {
            if (status.entry().category() == SettingCategory.SERVER_LINE) {
                statuses.add(status);
            }
        }
        assertEquals(2, statuses.size());
        assertEquals("motd", statuses.get(0).entry().name());
        assertEquals(StartReport.Outcome.NOT_APPLIED, statuses.get(0).outcome());
        assertEquals("not used in singleplayer", statuses.get(0).reason());
        assertEquals("difficulty", statuses.get(1).entry().name());
        assertEquals(StartReport.Outcome.ERROR, statuses.get(1).outcome());
        assertEquals("server lines: 0 applied, 0 overridden by the pack file, 1 not applied, 1 error",
                phase.report().summaryLines(SettingCategory.SERVER_LINE).get(0));
    }

    @Test
    void bothLinesOfARepeatedServerLineHaveTheErrorStatusInSingleplayer() {
        EntrySet entries = set(new Entry(SettingCategory.SERVER_LINE, Source.PLAYER, "pvp", "false", 1),
                new Entry(SettingCategory.SERVER_LINE, Source.PLAYER, "pvp", "true", 2),
                new Entry(SettingCategory.SERVER_LINE, Source.PACK, "pvp", "false", 1));
        phase.reportServerLinesInSingleplayer(entries);
        List<StartReport.Outcome> outcomes = new ArrayList<StartReport.Outcome>();
        for (StartReport.Status status : phase.report().statuses()) {
            outcomes.add(status.outcome());
        }
        assertEquals(Arrays.asList(StartReport.Outcome.ERROR, StartReport.Outcome.ERROR, StartReport.Outcome.NOT_APPLIED), outcomes);
        assertEquals(1, phase.report().problems(SettingCategory.SERVER_LINE).size());
    }

    @Test
    void aRemovedEntryLeavesTheValueAsItIs() {
        world.values.put("doMobSpawning", "false");
        phase.firstTick(set(), world);
        assertEquals("false", world.values.get("doMobSpawning"));
    }

    @Test
    void unreadableFilesStopTheWorld() {
        phase.firstTick(null, world);
        assertEquals(1, blocker.afterLoad.size());
    }
}
