package com.mahghuuuls.somepackdefaults.rules;

import com.mahghuuuls.somepackdefaults.config.Entry;
import com.mahghuuuls.somepackdefaults.config.EntrySet;
import com.mahghuuuls.somepackdefaults.config.MalformedLine;
import com.mahghuuuls.somepackdefaults.config.SettingCategory;
import com.mahghuuuls.somepackdefaults.config.Source;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Every check of an entry set. It reports problems and never changes the entries. Every entry of
 * both files is checked, also a player entry the pack file overrides, so a player always sees a
 * mistake in the player file.
 */
public final class EntryChecker {

    private static final String SERVER_LINES_ALLOWED = "a vanilla 1.12.2 server line other than the host lines "
            + String.join(", ", ServerLineTable.hostLineNames());

    private EntryChecker() {
    }

    /**
     * Lines of either file that are not {@code name=value} entries. Such a line could be meant as a
     * server line or a gamerule, so the caller reports it with the checks that stop the start.
     */
    public static List<Problem> checkLines(EntrySet set) {
        List<Problem> problems = new ArrayList<Problem>();
        for (MalformedLine line : set.malformedLines()) {
            problems.add(new Problem(line.fileName(), line.lineNumber(), line.text(), line.reason(),
                    "name=value, such as pvp=false or keepInventory=true"));
        }
        return problems;
    }

    /**
     * Checks the server-line entries of both files: host lines, {@code level-name} in the pack file,
     * values, and names repeated in one file. A {@code level-type} value is checked here only for
     * being present; whether the world type exists is known only after every mod has registered its types.
     */
    public static List<Problem> checkServerLines(EntrySet set) {
        List<Problem> problems = new ArrayList<Problem>();
        for (Entry entry : set.entries(SettingCategory.SERVER_LINE)) {
            String name = entry.name();
            if (ServerLineTable.isHostLine(name)) {
                problems.add(new Problem(entry.fileName(), entry.lineNumber(), entry.text(),
                        name + " is a host line, which belongs to the server admin and is not set by this mod", SERVER_LINES_ALLOWED));
            } else if (name.equals(ServerLineTable.LEVEL_NAME) && entry.source() == Source.PACK) {
                problems.add(new Problem(entry.fileName(), entry.lineNumber(), entry.text(),
                        "level-name belongs in " + Source.PLAYER.fileName() + " (the pack can suggest it in suggestions.properties)",
                        "every server line except level-name and the host lines"));
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
     * Checks the gamerule entries before a world loads: repeated names, and the values of entries
     * that name a vanilla gamerule. An entry whose name is not vanilla may belong to a mod that adds
     * its gamerule only to a loaded world, so it is left for {@link #checkGamerules}.
     */
    public static List<Problem> checkGamerulesBeforeLoad(EntrySet set, GameruleLookup vanilla) {
        List<Problem> problems = new ArrayList<Problem>();
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
     * Checks the lines of both files and every gamerule entry against a loaded world: the gamerule
     * must exist there and the value must fit its kind, as the /gamerule command requires. Nothing
     * is ever created. A name that is neither a server line nor a gamerule of this world is reported
     * here, because every name that is not a server line is read as a gamerule.
     */
    public static List<Problem> checkGamerules(EntrySet set, GameruleLookup world) {
        List<Problem> problems = checkLines(set);
        for (Entry entry : set.entries(SettingCategory.GAMERULE)) {
            GameruleLookup.Kind kind = world.kindOf(entry.name());
            if (kind == null) {
                problems.add(new Problem(entry.fileName(), entry.lineNumber(), entry.text(),
                        "this world has no gamerule named " + entry.name() + " (a typo, or the mod that adds it is not installed)",
                        "a gamerule this world has (/gamerule lists them), or a vanilla 1.12.2 server line; only vanilla server lines are supported"));
            } else {
                addValueProblem(entry, kind, problems);
            }
        }
        problems.addAll(repeatedNames(set, SettingCategory.GAMERULE));
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

    /**
     * A name may appear once in each file. The same name in both files is not a problem: the pack
     * file wins ({@link EntryResolution}).
     */
    static List<Problem> repeatedNames(EntrySet set, SettingCategory category) {
        List<Problem> problems = new ArrayList<Problem>();
        Map<String, Entry> first = new HashMap<String, Entry>();
        for (Entry entry : set.entries(category)) {
            String key = entry.source() + "/" + entry.name();
            Entry earlier = first.get(key);
            if (earlier == null) {
                first.put(key, entry);
                continue;
            }
            problems.add(new Problem(entry.fileName(), entry.lineNumber(), entry.text(),
                    entry.name() + " appears twice in this file (first at line " + earlier.lineNumber() + ")", "each name once per file"));
        }
        return problems;
    }
}
