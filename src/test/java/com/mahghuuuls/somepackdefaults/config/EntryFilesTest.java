package com.mahghuuuls.somepackdefaults.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntryFilesTest {

    /** A small stand-in for the server-line list, so this package is tested on its own. */
    private static final NameKinds KINDS = new NameKinds() {
        @Override
        public boolean isServerLine(String name) {
            return Arrays.asList("pvp", "motd", "level-name", "view-distance", "server-port").contains(name);
        }
    };

    private static List<Entry> parse(String text, List<MalformedLine> malformed) throws IOException {
        List<Entry> entries = new ArrayList<Entry>();
        EntryFiles.parse(Source.PLAYER, text, KINDS, entries, malformed);
        return entries;
    }

    private static String text(Path file) throws IOException {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
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
        assertEquals("player.properties", entries.get(0).fileName());
        assertTrue(malformed.isEmpty());
    }

    @Test
    void theKindOfAnEntryComesFromItsName() throws IOException {
        List<Entry> entries = parse("pvp=false\ndoDaylightCycle=false\nserver-port=1\n", new ArrayList<MalformedLine>());
        assertEquals(SettingCategory.SERVER_LINE, entries.get(0).category());
        assertEquals(SettingCategory.GAMERULE, entries.get(1).category());
        assertEquals(SettingCategory.SERVER_LINE, entries.get(2).category());
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
        assertEquals("player.properties", malformed.get(0).fileName());
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
    void aFreshFolderGetsThreeFilesWithNoActiveEntry(@TempDir Path dir) throws IOException {
        EntryFiles files = new EntryFiles(dir.resolve("somepackdefaults"), KINDS);
        FileCreation creation = files.createMissingFiles();
        assertEquals(Arrays.asList("pack.properties", "suggestions.properties", "player.properties"), creation.createdFiles());
        assertFalse(creation.playerFileFromPackSuggestions());
        for (String name : creation.createdFiles()) {
            Path file = files.folder().resolve(name);
            String text = text(file);
            for (char c : text.toCharArray()) {
                assertTrue(c < 128, "non-ASCII character in " + file);
            }
        }
        EntrySet set = files.read();
        assertTrue(set.entries().isEmpty());
        assertTrue(set.malformedLines().isEmpty());
        String player = text(files.folder().resolve("player.properties"));
        assertTrue(player.startsWith("# Some Pack Defaults: the PLAYER file."), player);
        assertTrue(player.contains("#keepInventory=false"), player);
        assertFalse(player.contains("##"), player);
    }

    @Test
    void theTemplatesHoldTheExampleLines(@TempDir Path dir) throws IOException {
        EntryFiles files = new EntryFiles(dir, KINDS);
        files.createMissingFiles();
        String pack = text(dir.resolve("pack.properties"));
        for (String name : new String[] {"allow-flight", "allow-nether", "pvp", "enable-command-block", "spawn-animals", "spawn-monsters", "spawn-npcs",
                "max-tick-time", "resource-pack", "resource-pack-sha1", "doDaylightCycle", "doWeatherCycle", "doMobSpawning", "doLimitedCrafting",
                "randomTickSpeed", "maxEntityCramming", "disableElytraMovementCheck"}) {
            assertTrue(pack.contains("\n#" + name + "="), name);
        }
        String suggestions = text(dir.resolve("suggestions.properties"));
        for (String name : new String[] {"level-name", "level-seed", "level-type", "generator-settings", "generate-structures", "motd", "difficulty",
                "gamemode", "max-players", "view-distance", "spawn-protection", "keepInventory", "naturalRegeneration", "mobGriefing", "doFireTick",
                "showDeathMessages", "announceAdvancements", "spawnRadius"}) {
            assertTrue(suggestions.contains("\n#" + name + "="), name);
        }
        assertTrue(pack.contains("Never ship player.properties"), pack);
        assertTrue(suggestions.contains("Never ship player.properties"), suggestions);
    }

    @Test
    void thePlayerFileIsMadeFromThePacksSuggestionsWithoutThePackMakerNotes(@TempDir Path dir) throws IOException {
        Files.write(dir.resolve("suggestions.properties"), "## note for the pack maker\n  ## indented note\n# for the player\nview-distance=10\r\nkeepInventory=true"
                .getBytes(StandardCharsets.UTF_8));
        EntryFiles files = new EntryFiles(dir, KINDS);
        FileCreation creation = files.createMissingFiles();
        assertEquals(Arrays.asList("pack.properties", "player.properties"), creation.createdFiles());
        assertTrue(creation.playerFileFromPackSuggestions());
        String player = text(dir.resolve("player.properties"));
        // The header's own line ends follow the checkout; the copied lines keep the suggestions' bytes.
        assertTrue(player.startsWith("# Some Pack Defaults: the PLAYER file."), player);
        assertTrue(player.endsWith("\n# for the player\nview-distance=10\r\nkeepInventory=true"), player);
        assertFalse(player.contains("note"), player);
        EntrySet set = files.read();
        assertEquals(2, set.entries().size());
        assertEquals(Source.PLAYER, set.entries().get(0).source());
    }

    @Test
    void theCopyKeepsEveryOtherByteAndDropsAByteOrderMark() {
        byte[] suggestions = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 'a', '=', (byte) 0xA7, '\n', '#', '#', '\n', '#', 'b'};
        assertArrayEquals(new byte[] {'a', '=', (byte) 0xA7, '\n', '#', 'b'}, EntryFiles.withoutPackMakerNotes(suggestions));
    }

    @Test
    void anExistingFileIsNeverRewritten(@TempDir Path dir) throws IOException {
        byte[] mine = "pvp=false\n".getBytes(StandardCharsets.UTF_8);
        byte[] shipped = "# shipped\nmotd=Pack\n".getBytes(StandardCharsets.UTF_8);
        Files.write(dir.resolve("player.properties"), mine);
        Files.write(dir.resolve("pack.properties"), shipped);
        Files.write(dir.resolve("suggestions.properties"), "pvp=true\n".getBytes(StandardCharsets.UTF_8));
        EntryFiles files = new EntryFiles(dir, KINDS);
        assertTrue(files.createMissingFiles().createdFiles().isEmpty());
        files.read();
        files.createMissingFiles();
        assertArrayEquals(mine, Files.readAllBytes(dir.resolve("player.properties")));
        assertArrayEquals(shipped, Files.readAllBytes(dir.resolve("pack.properties")));
        EntrySet set = files.read();
        assertEquals(Arrays.asList(Source.PACK, Source.PLAYER), Arrays.asList(set.entries().get(0).source(), set.entries().get(1).source()));
    }

    @Test
    void aMissingFileReadsAsNoEntries(@TempDir Path dir) throws IOException {
        assertTrue(new EntryFiles(dir.resolve("none"), KINDS).read().entries().isEmpty());
    }
}
