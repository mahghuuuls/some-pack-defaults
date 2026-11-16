package com.mahghuuuls.somepackdefaults.report;

import com.mahghuuuls.somepackdefaults.config.Entry;
import com.mahghuuuls.somepackdefaults.config.SettingCategory;
import com.mahghuuuls.somepackdefaults.rules.Problem;

import java.util.ArrayList;
import java.util.List;

/**
 * The text of the inspection command: every entry of this start with its setting category, file,
 * configured value, current value where it can be read, and status; then every error. It only
 * formats what the start reports hold and what {@link CurrentValues} gives; it reads nothing itself.
 */
public final class InspectionLines {

    /** Current values, read without side effects by the caller. */
    public interface CurrentValues {

        /** True in singleplayer, where server lines are not used. */
        boolean singleplayer();

        /** A server line's value in {@code server.properties}, or null when the file or the line is missing. */
        String fileValue(Entry serverLine);

        /** A server line's value in the running server, or a gamerule's value in the world; null when it cannot be read. */
        String runningValue(Entry entry);
    }

    private InspectionLines() {
    }

    public static List<String> lines(List<StartReport> reports, CurrentValues values) {
        List<String> lines = new ArrayList<String>();
        List<StartReport.Status> statuses = new ArrayList<StartReport.Status>();
        List<Problem> problems = new ArrayList<Problem>();
        for (SettingCategory category : SettingCategory.values()) {
            for (StartReport report : reports) {
                for (StartReport.Status status : report.statuses()) {
                    if (status.entry().category() == category) {
                        statuses.add(status);
                    }
                }
                problems.addAll(report.problems(category));
            }
        }
        lines.add("Some Pack Defaults inspection: " + statuses.size() + (statuses.size() == 1 ? " entry, " : " entries, ")
                + problems.size() + (problems.size() == 1 ? " error" : " errors") + (values.singleplayer() ? " (singleplayer)" : ""));
        for (StartReport.Status status : statuses) {
            lines.add(line(status, values));
        }
        for (Problem problem : problems) {
            lines.add("error: " + problem.message());
        }
        return lines;
    }

    static String line(StartReport.Status status, CurrentValues values) {
        Entry entry = status.entry();
        StringBuilder text = new StringBuilder()
                .append(entry.category().label()).append(' ').append(entry.name())
                .append(" (").append(entry.fileName()).append(" line ").append(entry.lineNumber()).append("): ")
                .append("configured ").append(entry.value());
        String current = current(entry, values);
        if (current != null) {
            text.append("; ").append(current);
        }
        text.append("; ").append(status.outcome().label());
        if (status.reason() != null) {
            text.append(": ").append(status.reason());
        }
        return text.toString();
    }

    private static String current(Entry entry, CurrentValues values) {
        String running = values.runningValue(entry);
        if (entry.category() != SettingCategory.SERVER_LINE) {
            return running == null ? "current unknown" : "current " + running;
        }
        if (values.singleplayer()) {
            return null;
        }
        String file = values.fileValue(entry);
        if (running == null) {
            return file == null ? "not in server.properties" : "in server.properties " + file + " (running value not readable)";
        }
        if (running.equals(file)) {
            return "current " + running;
        }
        return "in server.properties " + (file == null ? "(missing)" : file) + ", running " + running;
    }
}
