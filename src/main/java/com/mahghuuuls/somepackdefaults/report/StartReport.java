package com.mahghuuuls.somepackdefaults.report;

import com.mahghuuuls.somepackdefaults.config.Entry;
import com.mahghuuuls.somepackdefaults.config.EntryFiles;
import com.mahghuuuls.somepackdefaults.config.FileCreation;
import com.mahghuuuls.somepackdefaults.config.SettingCategory;
import com.mahghuuuls.somepackdefaults.rules.Problem;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** What the mod did with each entry during one start, and the log summary of it. */
public final class StartReport {

    /** Starts every line the mod writes to the log, whatever layout the log uses. */
    public static final String PREFIX = "Some Pack Defaults: ";

    /** What happened to one entry. */
    public enum Outcome {
        APPLIED("applied"),
        OVERRIDDEN("overridden by the pack file"),
        NOT_APPLIED("not applied"),
        /** An entry that has a problem; the problem itself is listed with the errors. */
        ERROR("error");

        private final String label;

        Outcome(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }
    }

    /** One entry's outcome and the reason, when there is one. */
    public static final class Status {
        private final Entry entry;
        private final Outcome outcome;
        private final String reason;

        Status(Entry entry, Outcome outcome, String reason) {
            this.entry = entry;
            this.outcome = outcome;
            this.reason = reason;
        }

        public Entry entry() {
            return entry;
        }

        public Outcome outcome() {
            return outcome;
        }

        public String reason() {
            return reason;
        }

        String line() {
            String text = entry.category().label() + " " + entry.text() + " (" + entry.fileName()
                    + " line " + entry.lineNumber() + "): " + outcome.label;
            return reason == null ? text : text + ": " + reason;
        }
    }

    private final List<Status> statuses = new ArrayList<Status>();
    private final Map<SettingCategory, List<Problem>> problems = new EnumMap<SettingCategory, List<Problem>>(SettingCategory.class);

    public void applied(Entry entry) {
        statuses.add(new Status(entry, Outcome.APPLIED, null));
    }

    /** A player entry whose setting the pack file also sets. */
    public void overridden(Entry entry) {
        statuses.add(new Status(entry, Outcome.OVERRIDDEN, null));
    }

    public void notApplied(Entry entry, String reason) {
        statuses.add(new Status(entry, Outcome.NOT_APPLIED, reason));
    }

    /** An entry whose problem is reported with {@link #problems}; used where a start goes on despite it. */
    public void error(Entry entry) {
        statuses.add(new Status(entry, Outcome.ERROR, null));
    }

    /** Marks every applied entry of a category as not applied, after its write was undone. */
    public void undoApplied(SettingCategory category, String reason) {
        for (int i = 0; i < statuses.size(); i++) {
            Status status = statuses.get(i);
            if (status.entry().category() == category && status.outcome() == Outcome.APPLIED) {
                statuses.set(i, new Status(status.entry(), Outcome.NOT_APPLIED, reason));
            }
        }
    }

    public void problems(SettingCategory category, List<Problem> found) {
        problemList(category).addAll(found);
    }

    public List<Status> statuses() {
        return Collections.unmodifiableList(statuses);
    }

    public List<Problem> problems(SettingCategory category) {
        return Collections.unmodifiableList(problemList(category));
    }

    private List<Problem> problemList(SettingCategory category) {
        List<Problem> list = problems.get(category);
        if (list == null) {
            list = new ArrayList<Problem>();
            problems.put(category, list);
        }
        return list;
    }

    /** One log line per created file, naming where a new player file came from. */
    public static List<String> creationLines(FileCreation creation) {
        List<String> lines = new ArrayList<String>();
        for (String file : creation.createdFiles()) {
            if (!file.equals(EntryFiles.PLAYER_FILE)) {
                lines.add("created " + file + " from its template");
            } else if (creation.playerFileFromPackSuggestions()) {
                lines.add("created " + file + " from the pack's " + EntryFiles.SUGGESTIONS_FILE);
            } else {
                lines.add("created " + file + " from the suggestions template (the pack ships no " + EntryFiles.SUGGESTIONS_FILE + ")");
            }
        }
        return lines;
    }

    /** The summary lines for one setting category: the counts, each entry's outcome, then each error. */
    public List<String> summaryLines(SettingCategory category) {
        List<String> lines = new ArrayList<String>();
        lines.add(countLine(category));
        for (String line : statusLines(category)) {
            lines.add(line);
        }
        for (String line : errorLines(category)) {
            lines.add(line);
        }
        return lines;
    }

    /** Writes the summary for one setting category, errors at error level, each line with the mod's prefix. */
    public void log(Logger logger, SettingCategory category) {
        logger.info(PREFIX + countLine(category));
        for (String line : statusLines(category)) {
            logger.info(PREFIX + line);
        }
        for (String line : errorLines(category)) {
            logger.error(PREFIX + line);
        }
    }

    private String countLine(SettingCategory category) {
        int[] counts = new int[Outcome.values().length];
        for (Status status : statuses) {
            if (status.entry().category() == category) {
                counts[status.outcome().ordinal()]++;
            }
        }
        return category.label() + "s: " + counts[Outcome.APPLIED.ordinal()] + " applied, " + counts[Outcome.OVERRIDDEN.ordinal()]
                + " overridden by the pack file, " + counts[Outcome.NOT_APPLIED.ordinal()] + " not applied, " + problemList(category).size()
                + (problemList(category).size() == 1 ? " error" : " errors");
    }

    private List<String> statusLines(SettingCategory category) {
        List<String> lines = new ArrayList<String>();
        for (Status status : statuses) {
            if (status.entry().category() == category) {
                lines.add("  " + status.line());
            }
        }
        return lines;
    }

    private List<String> errorLines(SettingCategory category) {
        List<String> lines = new ArrayList<String>();
        for (Problem problem : problemList(category)) {
            lines.add("  error: " + problem.message());
        }
        return lines;
    }
}
