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

    /**
     * Checks that a {@code level-type} entry names a world type the game knows. Vanilla would make
     * a normal world without a word when the name is unknown, so an unknown name is refused.
     * Returns null when the type is known.
     */
    public static Problem checkWorldType(Entry levelType, WorldTypeLookup worldTypes) {
        if (worldTypes.isKnown(levelType.value())) {
            return null;
        }
        return new Problem(levelType.fileName(), levelType.lineNumber(), levelType.text(), "no installed mod or Minecraft itself has this world type",
                "a registered world type: " + String.join(", ", worldTypes.knownNames()));
    }

    /**
     * Checks the gamerule files before a world loads: line form, repeated names, and the values of
     * entries that name a vanilla gamerule. An entry whose name is not vanilla may belong to a mod
     * that adds its gamerule only to a loaded world, so it is left for {@link #checkGamerules}.
     */
    public static List<Problem> checkGamerulesBeforeLoad(EntrySet set, GameruleLookup vanilla) {
        List<Problem> problems = gameruleLineProblems(set);
        for (Entry entry : set.entries(SettingCategory.GAMERULE)) {
            GameruleLookup.Kind kind = vanilla.kindOf(entry.name());
            if (kind != null) {
                addValueProblem(entry, kind, problems);
            }
        }
        problems.addAll(repeatedNames(set, SettingCategory.GAMERULE));
        return problems;
    }

    /**
     * Checks every gamerule entry against a loaded world: the gamerule must exist there and the value
     * must fit its kind, as the /gamerule command requires. Nothing is ever created.
     */
    public static List<Problem> checkGamerules(EntrySet set, GameruleLookup world) {
        List<Problem> problems = gameruleLineProblems(set);
        for (Entry entry : set.entries(SettingCategory.GAMERULE)) {
            GameruleLookup.Kind kind = world.kindOf(entry.name());
            if (kind == null) {
                problems.add(new Problem(entry.fileName(), entry.lineNumber(), entry.text(),
                        "this world has no gamerule named " + entry.name() + " (a typo, or the mod that adds it is not installed)",
                        "a gamerule this world has; /gamerule lists them"));
            } else {
                addValueProblem(entry, kind, problems);
            }
        }
        problems.addAll(repeatedNames(set, SettingCategory.GAMERULE));
        return problems;
    }

    private static List<Problem> gameruleLineProblems(EntrySet set) {
        List<Problem> problems = new ArrayList<Problem>();
        for (MalformedLine line : set.malformedLines(SettingCategory.GAMERULE)) {
            problems.add(new Problem(line.fileName(), line.lineNumber(), line.text(), line.reason(), "name=value, such as keepInventory=true"));
        }
        return problems;
    }

    private static void addValueProblem(Entry entry, GameruleLookup.Kind kind, List<Problem> problems) {
        String value = entry.value();
        boolean fits;
        switch (kind) {
            case TRUE_OR_FALSE:
                // Exactly as the /gamerule command accepts them: lower case only.
                fits = "true".equals(value) || "false".equals(value);
                break;
            case WHOLE_NUMBER:
                fits = ValueRule.anyInteger().check(value) == null;
                break;
            default:
                fits = true;
        }
        if (!fits) {
            problems.add(new Problem(entry.fileName(), entry.lineNumber(), entry.text(),
                    entry.name() + " takes " + kind.allowed(), kind.allowed()));
        }
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
