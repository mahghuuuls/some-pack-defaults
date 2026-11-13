package com.mahghuuuls.somepackdefaults.world;

import com.mahghuuuls.somepackdefaults.config.Entry;
import com.mahghuuuls.somepackdefaults.config.EntrySet;
import com.mahghuuuls.somepackdefaults.config.MalformedLine;
import com.mahghuuuls.somepackdefaults.config.Mode;
import com.mahghuuuls.somepackdefaults.config.SettingCategory;
import com.mahghuuuls.somepackdefaults.report.StartBlocker;
import com.mahghuuuls.somepackdefaults.rules.Problem;
import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
        }
    }

    private RecordingBlocker blocker;
    private WorldPhase phase;
    private MapWorld world;
    private int successfulTicks;

    @BeforeEach
    void setUp() {
        blocker = new RecordingBlocker();
        successfulTicks = 0;
        phase = new WorldPhase(blocker, LogManager.getLogger("test"), () -> successfulTicks++);
        world = new MapWorld();
    }

    @Test
    void aSuccessfulFirstTickIsReportedOnce() {
        phase.firstTick(set(rule(Mode.REAPPLY, "doDaylightCycle", "false", 1)), world);
        assertEquals(1, successfulTicks);
    }

    @Test
    void aStoppedFirstTickIsNotReportedAsSuccessful() {
        phase.firstTick(set(rule(Mode.REAPPLY, "keepInventroy", "true", 1)), world);
        phase.firstTick(null, world);
        assertEquals(0, successfulTicks);
    }

    private static Entry rule(Mode mode, String name, String value, int line) {
        return new Entry(SettingCategory.GAMERULE, mode, name, value, line);
    }

    private static EntrySet set(Entry... entries) {
        return new EntrySet(Arrays.asList(entries), Collections.<MalformedLine>emptyList());
    }

    @Test
    void setsReapplyGamerulesAndNothingElse() {
        phase.firstTick(set(rule(Mode.REAPPLY, "doDaylightCycle", "false", 1), rule(Mode.REAPPLY, "modFeature", "off", 2)), world);
        assertNull(blocker.afterLoad);
        assertEquals("false", world.values.get("doDaylightCycle"));
        assertEquals("off", world.values.get("modFeature"));
        assertEquals("false", world.values.get("keepInventory"));
        assertEquals("3", world.values.get("randomTickSpeed"));
        assertEquals("gamerules: 2 applied, 0 skipped, 0 not applied, 0 errors", phase.report().summaryLines(SettingCategory.GAMERULE).get(0));
    }

    @Test
    void anInvalidValueStopsAndChangesNothing() {
        phase.firstTick(set(rule(Mode.REAPPLY, "doDaylightCycle", "false", 1), rule(Mode.REAPPLY, "keepInventory", "yes", 2)), world);
        assertEquals(1, blocker.afterLoad.size());
        assertEquals("gamerules-reapply.properties line 2: keepInventory=yes: keepInventory takes true or false. Allowed: true or false.",
                blocker.afterLoad.get(0).message());
        assertEquals("true", world.values.get("doDaylightCycle"));
    }

    @Test
    void aMissingGameruleStopsAndIsNeverCreated() {
        phase.firstTick(set(rule(Mode.REAPPLY, "keepInventroy", "true", 1)), world);
        assertEquals(1, blocker.afterLoad.size());
        assertTrue(blocker.afterLoad.get(0).message().contains("this world has no gamerule named keepInventroy"), blocker.afterLoad.get(0).message());
        assertFalse(world.values.containsKey("keepInventroy"));
    }

    @Test
    void defaultGamerulesAreReportedNotApplied() {
        phase.firstTick(set(rule(Mode.DEFAULT, "keepInventory", "true", 1)), world);
        assertNull(blocker.afterLoad);
        assertEquals("false", world.values.get("keepInventory"));
        assertTrue(phase.report().summaryLines(SettingCategory.GAMERULE).get(0).contains("1 not applied"));
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
