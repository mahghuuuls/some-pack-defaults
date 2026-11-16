package com.mahghuuuls.somepackdefaults.world;

import com.mahghuuuls.somepackdefaults.config.Entry;
import com.mahghuuuls.somepackdefaults.config.EntryFiles;
import com.mahghuuuls.somepackdefaults.config.EntrySet;
import com.mahghuuuls.somepackdefaults.rules.EntryChecker;
import com.mahghuuuls.somepackdefaults.rules.Problem;
import com.mahghuuuls.somepackdefaults.rules.ServerLineTable;
import com.mahghuuuls.somepackdefaults.rules.WorldTypeLookup;
import net.minecraft.world.GameRules;
import net.minecraft.world.WorldType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The name rule against the real vanilla gamerules, and the shipped example lines against every check. */
class ShippedNamesTest {

    /** A commented-out example line: a # directly followed by name=value. */
    private static final Pattern EXAMPLE = Pattern.compile("^#[A-Za-z][A-Za-z0-9.-]*=.*$");

    /** The world types of vanilla 1.12.2, matched by name as the server matches level-type. */
    private static final WorldTypeLookup VANILLA_WORLD_TYPES = new WorldTypeLookup() {
        @Override
        public boolean isKnown(String name) {
            return WorldType.byName(name) != null;
        }

        @Override
        public List<String> knownNames() {
            return new ArrayList<String>();
        }
    };

    @Test
    void noVanillaGameruleHasAServerLineName() {
        String[] names = new GameRules().getRules();
        assertTrue(names.length > 20, "too few vanilla gamerules: " + names.length);
        for (String name : names) {
            assertFalse(ServerLineTable.NAME_KINDS.isServerLine(name), name);
        }
    }

    /** The template's text with every example line turned on, and how many were turned on. */
    private static String turnOnExamples(Path file, int[] count) throws IOException {
        StringBuilder text = new StringBuilder();
        for (String line : new String(Files.readAllBytes(file), StandardCharsets.UTF_8).split("\n", -1)) {
            String plain = line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
            if (EXAMPLE.matcher(plain).matches()) {
                text.append(plain.substring(1));
                count[0]++;
            } else {
                text.append(plain);
            }
            text.append('\n');
        }
        return text.toString();
    }

    @Test
    void everyShippedExampleLineIsValidWhenTurnedOn(@TempDir Path dir) throws IOException {
        Path templates = dir.resolve("templates");
        new EntryFiles(templates, ServerLineTable.NAME_KINDS).createMissingFiles();
        int[] packCount = {0};
        int[] suggestionCount = {0};
        Path files = dir.resolve("files");
        Files.createDirectories(files);
        Files.write(files.resolve("pack.properties"),
                turnOnExamples(templates.resolve("pack.properties"), packCount).getBytes(StandardCharsets.UTF_8));
        // Suggestions end up in the player file.
        Files.write(files.resolve("player.properties"),
                turnOnExamples(templates.resolve("suggestions.properties"), suggestionCount).getBytes(StandardCharsets.UTF_8));
        assertEquals(17, packCount[0]);
        assertEquals(18, suggestionCount[0]);
        EntrySet set = new EntryFiles(files, ServerLineTable.NAME_KINDS).read();
        assertEquals(35, set.entries().size());
        List<Problem> problems = new ArrayList<Problem>(EntryChecker.checkServerLines(set));
        problems.addAll(EntryChecker.checkGamerules(set, MinecraftGamerules.vanilla()));
        for (Entry entry : set.entries()) {
            if (entry.name().equals(ServerLineTable.LEVEL_TYPE)) {
                Problem worldType = EntryChecker.checkWorldType(entry, VANILLA_WORLD_TYPES);
                if (worldType != null) {
                    problems.add(worldType);
                }
            }
        }
        assertTrue(problems.isEmpty(), problems.toString());
    }
}
