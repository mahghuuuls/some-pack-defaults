package com.mahghuuuls.somepackdefaults.rules;

import com.mahghuuuls.somepackdefaults.config.Entry;
import com.mahghuuuls.somepackdefaults.config.EntrySet;
import com.mahghuuuls.somepackdefaults.config.SettingCategory;
import com.mahghuuuls.somepackdefaults.config.Source;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Which entry counts: every pack entry, and every player entry whose setting the pack
 * file does not set. The other player entries are overridden by the pack file. Built from a set
 * that passed its checks, so no name appears twice in one file.
 */
public final class EntryResolution {

    private final List<Entry> effective = new ArrayList<Entry>();
    private final List<Entry> overridden = new ArrayList<Entry>();

    private EntryResolution(EntrySet set) {
        Set<String> packNames = new HashSet<String>();
        for (Entry entry : set.entries()) {
            if (entry.source() == Source.PACK) {
                packNames.add(entry.name());
            }
        }
        for (Entry entry : set.entries()) {
            if (entry.source() == Source.PLAYER && packNames.contains(entry.name())) {
                overridden.add(entry);
            } else {
                effective.add(entry);
            }
        }
    }

    public static EntryResolution of(EntrySet set) {
        return new EntryResolution(set);
    }

    /** The entries to write or set, pack file first, each in file order. */
    public List<Entry> effective(SettingCategory category) {
        return of(effective, category);
    }

    /** The player entries whose setting the pack file also sets. */
    public List<Entry> overridden(SettingCategory category) {
        return of(overridden, category);
    }

    private static List<Entry> of(List<Entry> entries, SettingCategory category) {
        List<Entry> result = new ArrayList<Entry>();
        for (Entry entry : entries) {
            if (entry.category() == category) {
                result.add(entry);
            }
        }
        return result;
    }
}
