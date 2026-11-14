package com.mahghuuuls.somepackdefaults.world;

import com.mahghuuuls.somepackdefaults.config.Entry;
import com.mahghuuuls.somepackdefaults.config.EntrySet;
import com.mahghuuuls.somepackdefaults.config.MalformedLine;
import com.mahghuuuls.somepackdefaults.config.Mode;
import com.mahghuuuls.somepackdefaults.config.SettingCategory;
import com.mahghuuuls.somepackdefaults.report.StartBlocker;
import com.mahghuuuls.somepackdefaults.report.StartReport;
import com.mahghuuuls.somepackdefaults.rules.Problem;
import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
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

        final List<String> setOrder = new ArrayList<String>();

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
    private int successfulTicks;
    private NewWorldMark mark;

    @TempDir
    Path worldFolder;

    @BeforeEach
    void setUp() {
        mark = NewWorldMark.in(worldFolder);
        blocker = new RecordingBlocker();
        successfulTicks = 0;
        phase = new WorldPhase(blocker, LogManager.getLogger("test"), () -> successfulTicks++);
        world = new MapWorld();
    }

    @Test
    void aSuccessfulFirstTickIsReportedOnce() {
        phase.firstTick(set(rule(Mode.REAPPLY, "doDaylightCycle", "false", 1)), world, mark);
        assertEquals(1, successfulTicks);
    }

    @Test
    void aStoppedFirstTickIsNotReportedAsSuccessful() {
        phase.firstTick(set(rule(Mode.REAPPLY, "keepInventroy", "true", 1)), world, mark);
        phase.firstTick(null, world, mark);
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
        phase.firstTick(set(rule(Mode.REAPPLY, "doDaylightCycle", "false", 1), rule(Mode.REAPPLY, "modFeature", "off", 2)), world, mark);
        assertNull(blocker.afterLoad);
        assertEquals("false", world.values.get("doDaylightCycle"));
        assertEquals("off", world.values.get("modFeature"));
        assertEquals("false", world.values.get("keepInventory"));
        assertEquals("3", world.values.get("randomTickSpeed"));
        assertEquals("gamerules: 2 applied, 0 skipped, 0 not applied, 0 errors", phase.report().summaryLines(SettingCategory.GAMERULE).get(0));
    }

    @Test
    void anInvalidValueStopsAndChangesNothing() {
        phase.firstTick(set(rule(Mode.REAPPLY, "doDaylightCycle", "false", 1), rule(Mode.REAPPLY, "keepInventory", "yes", 2)), world, mark);
        assertEquals(1, blocker.afterLoad.size());
        assertEquals("gamerules-reapply.properties line 2: keepInventory=yes: keepInventory takes true or false. Allowed: true or false.",
                blocker.afterLoad.get(0).message());
        assertEquals("true", world.values.get("doDaylightCycle"));
    }

    @Test
    void aMissingGameruleStopsAndIsNeverCreated() {
        phase.firstTick(set(rule(Mode.REAPPLY, "keepInventroy", "true", 1)), world, mark);
        assertEquals(1, blocker.afterLoad.size());
        assertTrue(blocker.afterLoad.get(0).message().contains("this world has no gamerule named keepInventroy"), blocker.afterLoad.get(0).message());
        assertFalse(world.values.containsKey("keepInventroy"));
    }

    @Test
    void anExistingWorldSkipsDefaultGamerulesWithTheReason() {
        phase.firstTick(set(rule(Mode.DEFAULT, "keepInventory", "true", 1)), world, mark);
        assertNull(blocker.afterLoad);
        assertEquals("false", world.values.get("keepInventory"));
        StartReport.Status status = phase.report().statuses().get(0);
        assertEquals(StartReport.Outcome.SKIPPED, status.outcome());
        assertEquals("the world already exists", status.reason());
    }

    @Test
    void aNewWorldGetsDefaultsThenReapplyEntries() {
        phase.overworldCreated();
        // A Reapply entry for the same rule is refused as a repeat, so the order shows through two rules.
        world.values.put("randomTickSpeed", "3");
        phase.firstTick(set(rule(Mode.DEFAULT, "keepInventory", "true", 1), rule(Mode.REAPPLY, "randomTickSpeed", "5", 1)), world, mark);
        assertNull(blocker.afterLoad);
        assertEquals("true", world.values.get("keepInventory"));
        assertEquals("5", world.values.get("randomTickSpeed"));
        assertEquals(Arrays.asList("keepInventory", "randomTickSpeed"), world.setOrder);
        assertFalse(mark.exists());
    }

    @Test
    void aStoppedNewWorldIsMarkedAndGetsItsDefaultsAtTheNextSuccessfulOpen() {
        phase.overworldCreated();
        phase.firstTick(set(rule(Mode.DEFAULT, "keepInventory", "true", 1), rule(Mode.DEFAULT, "missingModRule", "1", 2)), world, mark);
        assertEquals(1, blocker.afterLoad.size());
        assertTrue(mark.exists());
        assertEquals("false", world.values.get("keepInventory"));

        WorldPhase next = new WorldPhase(blocker, LogManager.getLogger("test"), () -> successfulTicks++);
        next.firstTick(set(rule(Mode.DEFAULT, "keepInventory", "true", 1)), world, mark);
        assertEquals("true", world.values.get("keepInventory"));
        assertFalse(mark.exists(), "the mark is removed once the Defaults are applied");
        assertEquals(1, successfulTicks);
    }

    @Test
    void theMarkStaysUntilTheWorldIsSavedWithItsDefaults() {
        phase.overworldCreated();
        phase.firstTick(set(rule(Mode.DEFAULT, "missingModRule", "1", 1)), world, mark);
        assertTrue(mark.exists());
        int[] saves = {0};
        WorldPhase next = new WorldPhase(blocker, LogManager.getLogger("test"), () -> successfulTicks++);
        next.firstTick(set(rule(Mode.DEFAULT, "keepInventory", "true", 1)), world, mark, () -> saves[0]++);
        assertEquals("true", world.values.get("keepInventory"));
        assertEquals(1, saves[0], "the removal waits for the save");
        assertTrue(mark.exists(), "a crash before the save keeps the world new");
    }

    @Test
    void unreadableFilesOnANewWorldMarkIt() {
        phase.overworldCreated();
        phase.firstTick(null, world, mark);
        assertEquals(1, blocker.afterLoad.size());
        assertTrue(mark.exists());
    }

    @Test
    void aWorldCreatedOnThisStartWithoutAMarkNeedsNoRemoval() {
        phase.overworldCreated();
        int[] saves = {0};
        phase.firstTick(set(rule(Mode.DEFAULT, "keepInventory", "true", 1)), world, mark, () -> saves[0]++);
        assertEquals(0, saves[0]);
    }

    @Test
    void singleplayerReportsServerLinesAsUnusedAndTheirErrorsWithoutStopping() {
        Entry difficulty = new Entry(SettingCategory.SERVER_LINE, Mode.REAPPLY, "difficulty", "hardd", 1);
        Entry motd = new Entry(SettingCategory.SERVER_LINE, Mode.DEFAULT, "motd", "Pack", 1);
        EntrySet entries = set(difficulty, motd, rule(Mode.REAPPLY, "doDaylightCycle", "false", 1));
        phase.reportServerLinesInSingleplayer(entries);
        phase.firstTick(entries, world, mark);
        assertNull(blocker.afterLoad, "a server-line error does not stop a singleplayer world");
        assertEquals("false", world.values.get("doDaylightCycle"));
        assertEquals(1, phase.report().problems(SettingCategory.SERVER_LINE).size());
        assertTrue(phase.report().problems(SettingCategory.SERVER_LINE).get(0).message().startsWith("server-reapply.properties line 1: difficulty=hardd"));
        int unused = 0;
        for (StartReport.Status status : phase.report().statuses()) {
            if (status.entry().category() == SettingCategory.SERVER_LINE) {
                assertEquals(StartReport.Outcome.NOT_APPLIED, status.outcome());
                assertEquals("not used in singleplayer", status.reason());
                unused++;
            }
        }
        assertEquals(2, unused);
    }

    @Test
    void aStoppedExistingWorldIsNotMarked() {
        phase.firstTick(set(rule(Mode.REAPPLY, "keepInventroy", "true", 1)), world, mark);
        assertEquals(1, blocker.afterLoad.size());
        assertFalse(mark.exists());
    }

    @Test
    void aPackUpdateReachesAnExistingWorldOnlyThroughTheReapplyFile() {
        phase.firstTick(set(rule(Mode.DEFAULT, "doMobSpawning", "false", 1), rule(Mode.REAPPLY, "randomTickSpeed", "7", 1)), world, mark);
        assertEquals("true", world.values.get("doMobSpawning"));
        assertEquals("7", world.values.get("randomTickSpeed"));
    }

    @Test
    void theMarkIsWrittenReadAndRemovedInTheWorldFolder() throws IOException {
        assertEquals(worldFolder.resolve("somepackdefaults-new-world.json"), mark.path());
        assertFalse(mark.exists());
        mark.write(Instant.parse("2026-11-13T00:00:00Z"));
        assertTrue(mark.exists());
        assertTrue(new String(Files.readAllBytes(mark.path()), StandardCharsets.UTF_8).contains("\"writtenAt\": \"2026-11-13T00:00:00Z\""));
        mark.remove();
        assertFalse(mark.exists());
    }

    @Test
    void aRemovedEntryLeavesTheValueAsItIs() {
        world.values.put("doMobSpawning", "false");
        phase.firstTick(set(), world, mark);
        assertEquals("false", world.values.get("doMobSpawning"));
    }

    @Test
    void unreadableFilesStopTheWorld() {
        phase.firstTick(null, world, mark);
        assertEquals(1, blocker.afterLoad.size());
    }
}
