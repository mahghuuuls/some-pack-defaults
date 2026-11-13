package com.mahghuuuls.somepackdefaults.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Everything one read of the four configuration files produced, in file order. */
public final class EntrySet {

    private final List<Entry> entries;
    private final List<MalformedLine> malformedLines;

    public EntrySet(List<Entry> entries, List<MalformedLine> malformedLines) {
        this.entries = Collections.unmodifiableList(new ArrayList<Entry>(entries));
        this.malformedLines = Collections.unmodifiableList(new ArrayList<MalformedLine>(malformedLines));
    }

    public List<Entry> entries() {
        return entries;
    }

    public List<Entry> entries(SettingCategory category) {
        List<Entry> result = new ArrayList<Entry>();
        for (Entry entry : entries) {
            if (entry.category() == category) {
                result.add(entry);
            }
        }
        return result;
    }

    public List<Entry> entries(SettingCategory category, Mode mode) {
        List<Entry> result = new ArrayList<Entry>();
        for (Entry entry : entries) {
            if (entry.category() == category && entry.mode() == mode) {
                result.add(entry);
            }
        }
        return result;
    }

    public List<MalformedLine> malformedLines(SettingCategory category) {
        List<MalformedLine> result = new ArrayList<MalformedLine>();
        for (MalformedLine line : malformedLines) {
            if (line.category() == category) {
                result.add(line);
            }
        }
        return result;
    }
}
