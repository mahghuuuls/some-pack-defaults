package com.mahghuuuls.somepackdefaults.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Everything one read of the pack file and the player file produced: pack file first, each in file order. */
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

    public List<MalformedLine> malformedLines() {
        return malformedLines;
    }
}
