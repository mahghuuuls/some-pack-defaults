package com.mahghuuuls.somepackdefaults.report;

import com.mahghuuuls.somepackdefaults.config.Entry;
import com.mahghuuuls.somepackdefaults.config.Mode;
import com.mahghuuuls.somepackdefaults.config.SettingCategory;
import com.mahghuuuls.somepackdefaults.rules.Problem;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The inspection text for each status and value case, from fixed reports and values. */
class InspectionLinesTest {

    private static final class Values implements InspectionLines.CurrentValues {
        final boolean singleplayer;
        final Map<String, String> file = new HashMap<String, String>();
        final Map<String, String> running = new HashMap<String, String>();

        Values(boolean singleplayer) {
            this.singleplayer = singleplayer;
        }

        @Override
        public boolean singleplayer() {
            return singleplayer;
        }

        @Override
        public String fileValue(Entry serverLine) {
            return file.get(serverLine.name());
        }

        @Override
        public String runningValue(Entry entry) {
            return running.get(entry.name());
        }
    }

    private static Entry line(Mode mode, String name, String value) {
        return new Entry(SettingCategory.SERVER_LINE, mode, name, value, 1);
    }

    private static Entry rule(Mode mode, String name, String value) {
        return new Entry(SettingCategory.GAMERULE, mode, name, value, 2);
    }

    @Test
    void aSkippedDefaultLineShowsItsConfiguredAndCurrentValueAndWhy() {
        StartReport report = new StartReport();
        report.skipped(line(Mode.DEFAULT, "motd", "Pack Server"), "the setup already exists");
        Values values = new Values(false);
        values.file.put("motd", "Admin Server");
        values.running.put("motd", "Admin Server");
        List<String> lines = InspectionLines.lines(Collections.singletonList(report), values);
        assertEquals("Some Pack Defaults inspection: 1 entry, 0 errors", lines.get(0));
        assertEquals("server line motd (Default, server-default.properties line 1): configured Pack Server; current Admin Server; skipped: the setup already exists",
                lines.get(1));
    }

    @Test
    void aRunningValueThatDiffersFromTheFileShowsBoth() {
        StartReport report = new StartReport();
        report.applied(line(Mode.REAPPLY, "pvp", "false"));
        Values values = new Values(false);
        values.file.put("pvp", "true");
        values.running.put("pvp", "false");
        assertEquals("server line pvp (Reapply, server-reapply.properties line 1): configured false; in server.properties true, running false; applied",
                InspectionLines.lines(Collections.singletonList(report), values).get(1));
    }

    @Test
    void aLineWithoutAReadableRunningValueShowsTheFileValueOrItsAbsence() {
        StartReport report = new StartReport();
        report.applied(line(Mode.REAPPLY, "spawn-protection", "0"));
        report.applied(line(Mode.REAPPLY, "enable-command-block", "true"));
        Values values = new Values(false);
        values.file.put("spawn-protection", "0");
        List<String> lines = InspectionLines.lines(Collections.singletonList(report), values);
        assertEquals("server line spawn-protection (Reapply, server-reapply.properties line 1): configured 0; in server.properties 0 (running value not readable); applied",
                lines.get(1));
        assertEquals("server line enable-command-block (Reapply, server-reapply.properties line 1): configured true; not in server.properties; applied",
                lines.get(2));
    }

    @Test
    void singleplayerShowsServerLinesAsUnusedWithoutValuesAndListsErrors() {
        StartReport report = new StartReport();
        report.notApplied(line(Mode.REAPPLY, "difficulty", "hardd"), "not used in singleplayer");
        report.problems(SettingCategory.SERVER_LINE, Collections.singletonList(
                new Problem("server-reapply.properties", 1, "difficulty=hardd", "not a whole number", "0 to 3")));
        List<String> lines = InspectionLines.lines(Collections.singletonList(report), new Values(true));
        assertEquals("Some Pack Defaults inspection: 1 entry, 1 error (singleplayer)", lines.get(0));
        assertEquals("server line difficulty (Reapply, server-reapply.properties line 1): configured hardd; not applied: not used in singleplayer", lines.get(1));
        assertEquals("error: server-reapply.properties line 1: difficulty=hardd: not a whole number. Allowed: 0 to 3.", lines.get(2));
    }

    @Test
    void gamerulesShowTheWorldValueAndServerLinesComeFirst() {
        StartReport serverLines = new StartReport();
        serverLines.applied(line(Mode.REAPPLY, "motd", "Pack"));
        StartReport gamerules = new StartReport();
        gamerules.skipped(rule(Mode.DEFAULT, "keepInventory", "true"), "the world already exists");
        gamerules.applied(rule(Mode.REAPPLY, "modRule", "on"));
        Values values = new Values(false);
        values.file.put("motd", "Pack");
        values.running.put("motd", "Pack");
        values.running.put("keepInventory", "false");
        List<String> lines = InspectionLines.lines(Arrays.asList(gamerules, serverLines), values);
        assertEquals("server line motd (Reapply, server-reapply.properties line 1): configured Pack; current Pack; applied", lines.get(1));
        assertEquals("gamerule keepInventory (Default, gamerules-default.properties line 2): configured true; current false; skipped: the world already exists", lines.get(2));
        assertEquals("gamerule modRule (Reapply, gamerules-reapply.properties line 2): configured on; current unknown; applied", lines.get(3));
    }
}
