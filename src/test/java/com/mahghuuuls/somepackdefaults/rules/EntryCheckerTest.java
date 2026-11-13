package com.mahghuuuls.somepackdefaults.rules;

import com.mahghuuuls.somepackdefaults.config.Entry;
import com.mahghuuuls.somepackdefaults.config.EntrySet;
import com.mahghuuuls.somepackdefaults.config.MalformedLine;
import com.mahghuuuls.somepackdefaults.config.Mode;
import com.mahghuuuls.somepackdefaults.config.SettingCategory;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntryCheckerTest {

    private static Entry server(Mode mode, String name, String value, int line) {
        return new Entry(SettingCategory.SERVER_LINE, mode, name, value, line);
    }

    private static List<Problem> check(Entry... entries) {
        return EntryChecker.checkServerLines(new EntrySet(Arrays.asList(entries), Collections.<MalformedLine>emptyList()));
    }

    @Test
    void validEntriesHaveNoProblem() {
        assertTrue(check(server(Mode.REAPPLY, "pvp", "false", 1), server(Mode.DEFAULT, "motd", "Pack", 1)).isEmpty());
    }

    @Test
    void valueProblemNamesFileLineEntryProblemAndAllowedValues() {
        List<Problem> problems = check(server(Mode.REAPPLY, "gamemode", "9", 4));
        assertEquals(1, problems.size());
        assertEquals("server-reapply.properties line 4: gamemode=9: out of range. Allowed: 0 to 3.", problems.get(0).message());
    }

    @Test
    void hostLineIsRefusedAsTheAdminsLine() {
        assertEquals("server-reapply.properties line 2: server-port=25570: server-port is a host line, which belongs to the server admin "
                + "and is not set by the pack. Allowed: a vanilla 1.12.2 server line other than the host lines server-ip, server-port, enable-rcon, rcon.port, rcon.password, enable-query, query.port.",
                check(server(Mode.REAPPLY, "server-port", "25570", 2)).get(0).message());
    }

    @Test
    void lineFromAnotherModIsRefusedAsNotVanilla() {
        assertEquals("server-default.properties line 1: defaultworldgenerator-port=x: defaultworldgenerator-port is not a vanilla Minecraft "
                + "1.12.2 server line; only vanilla lines are supported. Allowed: a vanilla 1.12.2 server line other than the host lines server-ip, server-port, enable-rcon, rcon.port, rcon.password, enable-query, query.port.",
                check(server(Mode.DEFAULT, "defaultworldgenerator-port", "x", 1)).get(0).message());
    }

    @Test
    void nameInBothFilesIsReportedForBothFiles() {
        List<Problem> problems = check(server(Mode.DEFAULT, "pvp", "false", 3), server(Mode.REAPPLY, "pvp", "false", 5));
        assertEquals(2, problems.size());
        List<String> files = new ArrayList<String>();
        for (Problem problem : problems) {
            files.add(problem.fileName());
            assertTrue(problem.message().contains("in both the Default and the Reapply file"), problem.message());
        }
        assertTrue(files.contains("server-default.properties"));
        assertTrue(files.contains("server-reapply.properties"));
    }

    @Test
    void nameTwiceInOneFileIsReported() {
        List<Problem> problems = check(server(Mode.REAPPLY, "pvp", "false", 1), server(Mode.REAPPLY, "pvp", "true", 2));
        assertEquals(1, problems.size());
        assertEquals(2, problems.get(0).lineNumber());
        assertTrue(problems.get(0).message().contains("appears twice in this file (first at line 1)"));
    }

    @Test
    void malformedServerLineIsReported() {
        EntrySet set = new EntrySet(Collections.<Entry>emptyList(),
                Collections.singletonList(new MalformedLine(SettingCategory.SERVER_LINE, Mode.REAPPLY, 7, "=false", "not a name=value line")));
        List<Problem> problems = EntryChecker.checkServerLines(set);
        assertEquals("server-reapply.properties line 7: =false: not a name=value line. Allowed: name=value, as in server.properties.", problems.get(0).message());
    }

    @Test
    void gameruleEntriesAreNotPartOfTheServerLineCheck() {
        Entry gamerule = new Entry(SettingCategory.GAMERULE, Mode.REAPPLY, "pvp", "banana", 1);
        assertTrue(EntryChecker.checkServerLines(new EntrySet(Collections.singletonList(gamerule), Collections.<MalformedLine>emptyList())).isEmpty());
    }
}
