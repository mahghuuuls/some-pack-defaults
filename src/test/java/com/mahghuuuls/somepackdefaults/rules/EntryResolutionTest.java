package com.mahghuuuls.somepackdefaults.rules;

import com.mahghuuuls.somepackdefaults.config.Entry;
import com.mahghuuuls.somepackdefaults.config.EntrySet;
import com.mahghuuuls.somepackdefaults.config.MalformedLine;
import com.mahghuuuls.somepackdefaults.config.SettingCategory;
import com.mahghuuuls.somepackdefaults.config.Source;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntryResolutionTest {

    private static Entry entry(SettingCategory category, Source source, String name, String value, int line) {
        return new Entry(category, source, name, value, line);
    }

    private static List<String> texts(List<Entry> entries) {
        List<String> result = new ArrayList<String>();
        for (Entry entry : entries) {
            result.add(entry.fileName() + ":" + entry.text());
        }
        return result;
    }

    @Test
    void thePackFileWinsAndOnlyTheSameNameIsOverridden() {
        EntrySet set = new EntrySet(Arrays.asList(
                entry(SettingCategory.SERVER_LINE, Source.PACK, "pvp", "false", 1),
                entry(SettingCategory.GAMERULE, Source.PACK, "doDaylightCycle", "false", 2),
                entry(SettingCategory.SERVER_LINE, Source.PLAYER, "pvp", "true", 1),
                entry(SettingCategory.SERVER_LINE, Source.PLAYER, "view-distance", "12", 2),
                entry(SettingCategory.GAMERULE, Source.PLAYER, "keepInventory", "true", 3)), Collections.<MalformedLine>emptyList());
        EntryResolution resolution = EntryResolution.of(set);
        assertEquals(Arrays.asList("pack.properties:pvp=false", "player.properties:view-distance=12"),
                texts(resolution.effective(SettingCategory.SERVER_LINE)));
        assertEquals(Arrays.asList("pack.properties:doDaylightCycle=false", "player.properties:keepInventory=true"),
                texts(resolution.effective(SettingCategory.GAMERULE)));
        assertEquals(Collections.singletonList("player.properties:pvp=true"), texts(resolution.overridden(SettingCategory.SERVER_LINE)));
        assertTrue(resolution.overridden(SettingCategory.GAMERULE).isEmpty());
    }

    @Test
    void withoutAPackEntryEveryPlayerEntryCounts() {
        EntrySet set = new EntrySet(Collections.singletonList(entry(SettingCategory.GAMERULE, Source.PLAYER, "keepInventory", "true", 1)),
                Collections.<MalformedLine>emptyList());
        assertEquals(1, EntryResolution.of(set).effective(SettingCategory.GAMERULE).size());
        assertTrue(EntryResolution.of(set).overridden(SettingCategory.GAMERULE).isEmpty());
    }
}
