package com.mahghuuuls.somepackdefaults.rules;

import com.mahghuuuls.somepackdefaults.config.Entry;
import com.mahghuuuls.somepackdefaults.config.EntrySet;
import com.mahghuuuls.somepackdefaults.config.MalformedLine;
import com.mahghuuuls.somepackdefaults.config.Mode;
import com.mahghuuuls.somepackdefaults.config.SettingCategory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Every check of an entry set. It reports problems and never changes the entries. */
public final class EntryChecker {

    private static final String PACK_LINES_ALLOWED = "a vanilla 1.12.2 server line other than the host lines "
            + String.join(", ", ServerLineTable.hostLineNames());

    private EntryChecker() {
    }

    /**
     * Checks the two server-line files: line form, names, values, and names repeated in one file or
     * listed in both. A {@code level-type} value is checked here only for being present; whether
     * the world type exists is known only after every mod has registered its types.
     */
    public static List<Problem> checkServerLines(EntrySet set) {
        List<Problem> problems = new ArrayList<Problem>();
        for (MalformedLine line : set.malformedLines(SettingCategory.SERVER_LINE)) {
            problems.add(new Problem(line.fileName(), line.lineNumber(), line.text(), line.reason(), "name=value, as in server.properties"));
        }
        for (Entry entry : set.entries(SettingCategory.SERVER_LINE)) {
            String name = entry.name();
            if (ServerLineTable.isHostLine(name)) {
                problems.add(new Problem(entry.fileName(), entry.lineNumber(), entry.text(),
                        name + " is a host line, which belongs to the server admin and is not set by the pack", PACK_LINES_ALLOWED));
            } else if (!ServerLineTable.isPackLine(name)) {
                problems.add(new Problem(entry.fileName(), entry.lineNumber(), entry.text(),
                        name + " is not a vanilla Minecraft 1.12.2 server line; only vanilla lines are supported", PACK_LINES_ALLOWED));
            } else {
                String wrong = ServerLineTable.check(name, entry.value());
                if (wrong != null) {
                    problems.add(new Problem(entry.fileName(), entry.lineNumber(), entry.text(), wrong, ServerLineTable.allowed(name)));
                }
            }
        }
        problems.addAll(repeatedNames(set, SettingCategory.SERVER_LINE));
        return problems;
    }

    /** A name may appear once per setting category: once in one file, never in both. */
    static List<Problem> repeatedNames(EntrySet set, SettingCategory category) {
        List<Problem> problems = new ArrayList<Problem>();
        Map<String, Entry> first = new HashMap<String, Entry>();
        for (Entry entry : set.entries(category)) {
            Entry earlier = first.get(entry.name());
            if (earlier == null) {
                first.put(entry.name(), entry);
                continue;
            }
            String where = earlier.fileName() + " line " + earlier.lineNumber();
            String problem = earlier.mode() == entry.mode()
                    ? entry.name() + " appears twice in this file (first at line " + earlier.lineNumber() + ")"
                    : entry.name() + " is in both the " + Mode.DEFAULT.label() + " and the " + Mode.REAPPLY.label() + " file (also at " + where + ")";
            problems.add(new Problem(entry.fileName(), entry.lineNumber(), entry.text(), problem, "each name once per setting category"));
            if (earlier.mode() != entry.mode()) {
                problems.add(new Problem(earlier.fileName(), earlier.lineNumber(), earlier.text(),
                        entry.name() + " is in both the " + Mode.DEFAULT.label() + " and the " + Mode.REAPPLY.label() + " file (also at "
                                + entry.fileName() + " line " + entry.lineNumber() + ")", "each name once per setting category"));
            }
        }
        return problems;
    }
}
