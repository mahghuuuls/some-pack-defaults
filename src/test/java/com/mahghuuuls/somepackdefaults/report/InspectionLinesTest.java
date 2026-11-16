package com.mahghuuuls.somepackdefaults.report;

import com.mahghuuuls.somepackdefaults.config.Entry;
import com.mahghuuuls.somepackdefaults.config.SettingCategory;
import com.mahghuuuls.somepackdefaults.config.Source;
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

    private static Entry line(Source source, String name, String value) {
        return new Entry(SettingCategory.SERVER_LINE, source, name, value, 1);
    }

    private static Entry rule(Source source, String name, String value) {
        return new Entry(SettingCategory.GAMERULE, source, name, value, 2);
    }

    @Test
    void anOverriddenPlayerLineShowsItsConfiguredAndCurrentValueAndWhy() {
        StartReport report = new StartReport();
        report.overridden(line(Source.PLAYER, "motd", "Mine"));
        Values values = new Values(false);
        values.file.put("motd", "Pack Server");
        values.running.put("motd", "Pack Server");
        List<String> lines = InspectionLines.lines(Collections.singletonList(report), values);
        assertEquals("Some Pack Defaults inspection: 1 entry, 0 errors", lines.get(0));
        assertEquals("server line motd (player.properties line 1): configured Mine; current Pack Server; overridden by the pack file",
                lines.get(1));
    }

    @Test
    void aRunningValueThatDiffersFromTheFileShowsBoth() {
        StartReport report = new StartReport();
        report.applied(line(Source.PACK, "pvp", "false"));
        Values values = new Values(false);
        values.file.put("pvp", "true");
        values.running.put("pvp", "false");
        assertEquals("server line pvp (pack.properties line 1): configured false; in server.properties true, running false; applied",
                InspectionLines.lines(Collections.singletonList(report), values).get(1));
    }

    @Test
    void aLineWithoutAReadableRunningValueShowsTheFileValueOrItsAbsence() {
        StartReport report = new StartReport();
        report.applied(line(Source.PACK, "spawn-protection", "0"));
        report.applied(line(Source.PACK, "enable-command-block", "true"));
        Values values = new Values(false);
        values.file.put("spawn-protection", "0");
        List<String> lines = InspectionLines.lines(Collections.singletonList(report), values);
        assertEquals("server line spawn-protection (pack.properties line 1): configured 0; in server.properties 0 (running value not readable); applied",
                lines.get(1));
        assertEquals("server line enable-command-block (pack.properties line 1): configured true; not in server.properties; applied",
                lines.get(2));
    }

    @Test
    void singleplayerShowsServerLinesAsUnusedWithoutValuesAndListsErrors() {
        StartReport report = new StartReport();
        report.notApplied(line(Source.PACK, "difficulty", "hardd"), "not used in singleplayer");
        report.problems(SettingCategory.SERVER_LINE, Collections.singletonList(
                new Problem("pack.properties", 1, "difficulty=hardd", "not a whole number", "0 to 3")));
        List<String> lines = InspectionLines.lines(Collections.singletonList(report), new Values(true));
        assertEquals("Some Pack Defaults inspection: 1 entry, 1 error (singleplayer)", lines.get(0));
        assertEquals("server line difficulty (pack.properties line 1): configured hardd; not applied: not used in singleplayer", lines.get(1));
        assertEquals("error: pack.properties line 1: difficulty=hardd: not a whole number. Allowed: 0 to 3.", lines.get(2));
    }

    @Test
    void gamerulesShowTheWorldValueAndServerLinesComeFirst() {
        StartReport serverLines = new StartReport();
        serverLines.applied(line(Source.PACK, "motd", "Pack"));
        StartReport gamerules = new StartReport();
        gamerules.overridden(rule(Source.PLAYER, "keepInventory", "true"));
        gamerules.applied(rule(Source.PACK, "modRule", "on"));
        Values values = new Values(false);
        values.file.put("motd", "Pack");
        values.running.put("motd", "Pack");
        values.running.put("keepInventory", "false");
        List<String> lines = InspectionLines.lines(Arrays.asList(gamerules, serverLines), values);
        assertEquals("server line motd (pack.properties line 1): configured Pack; current Pack; applied", lines.get(1));
        assertEquals("gamerule keepInventory (player.properties line 2): configured true; current false; overridden by the pack file", lines.get(2));
        assertEquals("gamerule modRule (pack.properties line 2): configured on; current unknown; applied", lines.get(3));
    }
}
