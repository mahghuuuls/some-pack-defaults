package com.mahghuuuls.somepackdefaults.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntryFilesTest {

    private static List<Entry> parse(String text, List<MalformedLine> malformed) throws IOException {
        List<Entry> entries = new ArrayList<Entry>();
        EntryFiles.parse(SettingCategory.SERVER_LINE, Mode.REAPPLY, text, entries, malformed);
        return entries;
    }

    @Test
    void readsEntriesWithLineNumbersAndSkipsCommentsAndBlankLines() throws IOException {
        List<MalformedLine> malformed = new ArrayList<MalformedLine>();
        List<Entry> entries = parse("# comment\n\n! also a comment\npvp=false\n   motd = Pack Server\n", malformed);
        assertEquals(2, entries.size());
        assertEquals("pvp", entries.get(0).name());
        assertEquals("false", entries.get(0).value());
        assertEquals(4, entries.get(0).lineNumber());
        assertEquals("motd", entries.get(1).name());
        assertEquals("Pack Server", entries.get(1).value());
        assertEquals(5, entries.get(1).lineNumber());
        assertEquals("server-reapply.properties", entries.get(0).fileName());
        assertTrue(malformed.isEmpty());
    }

    @Test
    void decodesEscapesTheWayServerPropertiesDoes() throws IOException {
        List<Entry> entries = parse("motd=\\u00A7aGreen\\: server\n", new ArrayList<MalformedLine>());
        assertEquals("§aGreen: server", entries.get(0).value());
    }

    @Test
    void keepsTrailingSpacesLikeVanilla() throws IOException {
        List<Entry> entries = parse("pvp=false \n", new ArrayList<MalformedLine>());
        assertEquals("false ", entries.get(0).value());
    }

    @Test
    void joinsContinuedLinesAndReportsTheFirstLine() throws IOException {
        List<Entry> entries = parse("# x\nmotd=Pack \\\n    Server\npvp=true\n", new ArrayList<MalformedLine>());
        assertEquals("Pack Server", entries.get(0).value());
        assertEquals(2, entries.get(0).lineNumber());
        assertEquals(4, entries.get(1).lineNumber());
    }

    @Test
    void keepsRepeatedNamesSoTheyCanBeReported() throws IOException {
        List<Entry> entries = parse("pvp=false\npvp=true\n", new ArrayList<MalformedLine>());
        assertEquals(2, entries.size());
    }

    @Test
    void reportsALineWithoutAName() throws IOException {
        List<MalformedLine> malformed = new ArrayList<MalformedLine>();
        List<Entry> entries = parse("=false\n", malformed);
        assertTrue(entries.isEmpty());
        assertEquals(1, malformed.size());
        assertEquals(1, malformed.get(0).lineNumber());
    }

    @Test
    void reportsAMalformedEscapeInsteadOfThrowing() throws IOException {
        List<MalformedLine> malformed = new ArrayList<MalformedLine>();
        List<Entry> entries = parse("level-name=saves\\ultra\npvp=false\n", malformed);
        assertEquals(1, entries.size());
        assertEquals(1, malformed.size());
        assertEquals(1, malformed.get(0).lineNumber());
        assertTrue(malformed.get(0).reason().contains("escape"), malformed.get(0).reason());
    }

    @Test
    void ignoresAByteOrderMark() throws IOException {
        List<Entry> entries = parse("\uFEFFpvp=false\n", new ArrayList<MalformedLine>());
        assertEquals("pvp", entries.get(0).name());
    }

    @Test
    void createsFourFilesWithNoActiveEntry(@TempDir Path dir) throws IOException {
        EntryFiles files = new EntryFiles(dir.resolve("somepackdefaults"));
        EntrySet set = files.read();
        for (SettingCategory category : SettingCategory.values()) {
            for (Mode mode : Mode.values()) {
                Path file = dir.resolve("somepackdefaults").resolve(category.fileName(mode));
                assertTrue(Files.exists(file), file.toString());
                String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                assertTrue(text.contains("Example"), file.toString());
                for (char c : text.toCharArray()) {
                    assertTrue(c < 128, "non-ASCII character in " + file);
                }
            }
        }
        assertTrue(set.entries().isEmpty());
        assertTrue(set.malformedLines(SettingCategory.SERVER_LINE).isEmpty());
        assertTrue(set.malformedLines(SettingCategory.GAMERULE).isEmpty());
    }

    @Test
    void neverRewritesAnExistingFile(@TempDir Path dir) throws IOException {
        Path folder = dir.resolve("somepackdefaults");
        Files.createDirectories(folder);
        Path file = folder.resolve("server-reapply.properties");
        byte[] mine = "pvp=false\n".getBytes(StandardCharsets.UTF_8);
        Files.write(file, mine);
        EntryFiles files = new EntryFiles(folder);
        files.read();
        files.read();
        assertArrayEquals(mine, Files.readAllBytes(file));
        assertEquals(1, files.read().entries(SettingCategory.SERVER_LINE, Mode.REAPPLY).size());
    }
}
