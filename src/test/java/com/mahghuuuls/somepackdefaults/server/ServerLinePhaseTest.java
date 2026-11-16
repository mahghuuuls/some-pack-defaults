package com.mahghuuuls.somepackdefaults.server;

import com.mahghuuuls.somepackdefaults.config.EntryFiles;
import com.mahghuuuls.somepackdefaults.config.SettingCategory;
import com.mahghuuuls.somepackdefaults.config.Source;
import com.mahghuuuls.somepackdefaults.report.StartBlocker;
import com.mahghuuuls.somepackdefaults.report.StartReport;
import com.mahghuuuls.somepackdefaults.rules.GameruleLookup;
import com.mahghuuuls.somepackdefaults.rules.Problem;
import com.mahghuuuls.somepackdefaults.rules.ServerLineTable;
import com.mahghuuuls.somepackdefaults.rules.WorldTypeLookup;
import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The pre-initialization step against real files in a temporary server folder. */
class ServerLinePhaseTest {

    @TempDir
    Path dir;

    private Path config;
    private Path serverProperties;
    private StartReport report;
    private RecordingBlocker blocker;

    private static final class RecordingBlocker implements StartBlocker {
        List<Problem> stoppedWith;

        @Override
        public void stopBeforeWorldLoad(List<Problem> problems) {
            stoppedWith = problems;
        }

        @Override
        public void stopAfterWorldLoad(List<Problem> problems) {
            throw new AssertionError("server lines never stop after a world loads");
        }
    }

    private static final GameruleLookup VANILLA_GAMERULES = new GameruleLookup() {
        @Override
        public Kind kindOf(String name) {
            return name.equals("keepInventory") ? Kind.TRUE_OR_FALSE : null;
        }
    };

    @BeforeEach
    void setUp() {
        config = dir.resolve("config").resolve("somepackdefaults");
        serverProperties = dir.resolve("server.properties");
        report = new StartReport();
        blocker = new RecordingBlocker();
    }

    private void file(String name, String text) throws IOException {
        Files.createDirectories(config);
        Files.write(config.resolve(name), text.getBytes(StandardCharsets.UTF_8));
    }

    private void pack(String text) throws IOException {
        file("pack.properties", text);
    }

    private void player(String text) throws IOException {
        file("player.properties", text);
    }

    /** A start as the game runs it: the world phase creates missing files, then this phase reads them. */
    private ServerLinePhase run() {
        EntryFiles files = new EntryFiles(config, ServerLineTable.NAME_KINDS);
        try {
            files.createMissingFiles();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        ServerLinePhase phase = new ServerLinePhase(serverProperties, report, blocker, LogManager.getLogger("test"), VANILLA_GAMERULES);
        phase.apply(files);
        return phase;
    }

    /** Runs a later start with a fresh report, as a new server process would. */
    private ServerLinePhase restart() {
        report = new StartReport();
        return run();
    }

    private Properties readServerProperties() throws IOException {
        Properties properties = new Properties();
        properties.load(new ByteArrayInputStream(Files.readAllBytes(serverProperties)));
        return properties;
    }

    private List<StartReport.Status> statuses(StartReport.Outcome outcome) {
        List<StartReport.Status> result = new ArrayList<StartReport.Status>();
        for (StartReport.Status status : report.statuses()) {
            if (status.outcome() == outcome) {
                result.add(status);
            }
        }
        return result;
    }

    /** A world-type registry that knows exactly the given names, matched as the game matches them. */
    private static WorldTypeLookup knowing(final String... names) {
        return new WorldTypeLookup() {
            @Override
            public boolean isKnown(String name) {
                for (String known : names) {
                    if (known.equalsIgnoreCase(name)) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            public List<String> knownNames() {
                return Arrays.asList(names);
            }
        };
    }

    @Test
    void theFirstStartMakesThePlayerFileFromTheSuggestionsAndUsesIt() throws IOException {
        file("suggestions.properties", "## for the pack maker\n# a comment for the player\nview-distance=10\n");
        run();
        String made = new String(Files.readAllBytes(config.resolve("player.properties")), StandardCharsets.UTF_8);
        assertTrue(made.endsWith("# a comment for the player\nview-distance=10\n"), made);
        assertFalse(made.contains("##"), made);
        assertEquals("10", readServerProperties().getProperty("view-distance"));
        assertNull(blocker.stoppedWith);
    }

    @Test
    void aChangedSuggestionsFileLeavesAnExistingPlayerFileAlone() throws IOException {
        file("suggestions.properties", "view-distance=10\n");
        run();
        byte[] before = Files.readAllBytes(config.resolve("player.properties"));
        file("suggestions.properties", "view-distance=12\nkeepInventory=true\n");
        restart();
        assertArrayEquals(before, Files.readAllBytes(config.resolve("player.properties")));
        assertEquals("10", readServerProperties().getProperty("view-distance"));
    }

    @Test
    void theFilesFromTheTemplatesChangeNoSetting() throws IOException {
        run();
        assertTrue(Files.exists(config.resolve("pack.properties")));
        assertTrue(Files.exists(config.resolve("suggestions.properties")));
        assertTrue(Files.exists(config.resolve("player.properties")));
        assertFalse(Files.exists(serverProperties));
        assertTrue(report.statuses().isEmpty());
        assertNull(blocker.stoppedWith);
    }

    @Test
    void writesTheLinesOfBothFilesAndKeepsOtherLines() throws IOException {
        Files.write(serverProperties, "view-distance=6\nmotd=Old\n".getBytes(StandardCharsets.ISO_8859_1));
        pack("motd=Pack Server\n");
        player("gamemode=2\n");
        run();
        Properties written = readServerProperties();
        assertEquals("Pack Server", written.getProperty("motd"));
        assertEquals("2", written.getProperty("gamemode"));
        assertEquals("6", written.getProperty("view-distance"));
        assertNull(blocker.stoppedWith);
        assertEquals("server lines: 2 applied, 0 overridden by the pack file, 0 not applied, 0 errors",
                report.summaryLines(SettingCategory.SERVER_LINE).get(0));
        assertEquals("  server line motd=Pack Server (pack.properties line 1): applied", report.summaryLines(SettingCategory.SERVER_LINE).get(1));
    }

    @Test
    void everyStartWritesTheLinesAgain() throws IOException {
        pack("pvp=false\n");
        player("view-distance=10\n");
        run();
        Files.write(serverProperties, "pvp=true\nview-distance=12\n".getBytes(StandardCharsets.ISO_8859_1));
        restart();
        assertEquals("false", readServerProperties().getProperty("pvp"));
        assertEquals("10", readServerProperties().getProperty("view-distance"));
    }

    @Test
    void thePlayerFileIsTheOnePlaceToChangeASetting() throws IOException {
        player("view-distance=10\n");
        run();
        player("view-distance=12\n");
        restart();
        assertEquals("12", readServerProperties().getProperty("view-distance"));
    }

    @Test
    void thePackFileWinsAndThePlayerLineIsReportedAsOverridden() throws IOException {
        pack("pvp=false\n");
        player("pvp=true\nmotd=Mine\n");
        run();
        assertEquals("false", readServerProperties().getProperty("pvp"));
        assertEquals("Mine", readServerProperties().getProperty("motd"));
        assertNull(blocker.stoppedWith);
        List<StartReport.Status> overridden = statuses(StartReport.Outcome.OVERRIDDEN);
        assertEquals(1, overridden.size());
        assertEquals(Source.PLAYER, overridden.get(0).entry().source());
        assertEquals("pvp", overridden.get(0).entry().name());
        assertTrue(report.summaryLines(SettingCategory.SERVER_LINE)
                .contains("  server line pvp=true (player.properties line 1): overridden by the pack file"), report.summaryLines(SettingCategory.SERVER_LINE).toString());
    }

    @Test
    void anInvalidPlayerLineStopsEvenWhenThePackOverridesIt() throws IOException {
        pack("pvp=false\n");
        player("pvp=maybe\n");
        run();
        assertEquals(1, blocker.stoppedWith.size());
        assertTrue(blocker.stoppedWith.get(0).message().startsWith("player.properties line 1: pvp=maybe"), blocker.stoppedWith.get(0).message());
        assertFalse(Files.exists(serverProperties));
    }

    @Test
    void aBadVanillaGameruleStopsBeforeAnyServerLineIsWrittenAndBothKindsAreReported() throws IOException {
        byte[] before = "motd=Old\n".getBytes(StandardCharsets.ISO_8859_1);
        Files.write(serverProperties, before);
        pack("motd=Pack Server\npvp=maybe\n");
        player("keepInventory=yes\nmodRule=anything\n");
        run();
        assertArrayEquals(before, Files.readAllBytes(serverProperties));
        assertEquals(2, blocker.stoppedWith.size());
        assertTrue(blocker.stoppedWith.get(0).message().startsWith("pack.properties line 2: pvp=maybe"), blocker.stoppedWith.get(0).message());
        assertTrue(blocker.stoppedWith.get(1).message().startsWith("player.properties line 1: keepInventory=yes"), blocker.stoppedWith.get(1).message());
    }

    @Test
    void levelNameInThePackFileStopsAndWritesNothing() throws IOException {
        byte[] before = "motd=Old\n".getBytes(StandardCharsets.ISO_8859_1);
        Files.write(serverProperties, before);
        pack("level-name=x\nmotd=Pack Server\n");
        run();
        assertArrayEquals(before, Files.readAllBytes(serverProperties));
        assertEquals(1, blocker.stoppedWith.size());
        assertTrue(blocker.stoppedWith.get(0).message().contains("level-name belongs in player.properties"), blocker.stoppedWith.get(0).message());
    }

    @Test
    void levelNameInThePlayerFileIsWritten() throws IOException {
        player("level-name=packworld\n");
        run();
        assertEquals("packworld", readServerProperties().getProperty("level-name"));
    }

    @Test
    void aHostLineInThePlayerFileStopsAndWritesNothing() throws IOException {
        byte[] before = "motd=Old\n".getBytes(StandardCharsets.ISO_8859_1);
        Files.write(serverProperties, before);
        player("server-port=25570\n");
        run();
        assertArrayEquals(before, Files.readAllBytes(serverProperties));
        assertTrue(blocker.stoppedWith.get(0).message().contains("server-port is a host line"), blocker.stoppedWith.get(0).message());
    }

    @Test
    void aNameTwiceInOneFileStops() throws IOException {
        player("pvp=false\npvp=false\n");
        run();
        assertEquals(1, blocker.stoppedWith.size());
        assertEquals("player.properties line 2: pvp=false: pvp appears twice in this file (first at line 1). Allowed: each name once per file.",
                blocker.stoppedWith.get(0).message());
    }

    @Test
    void aLineThatIsNotNameValueStops() throws IOException {
        pack("=false\n");
        run();
        assertEquals(1, blocker.stoppedWith.size());
        assertTrue(blocker.stoppedWith.get(0).message().startsWith("pack.properties line 1: =false: not a name=value line"), blocker.stoppedWith.get(0).message());
    }

    @Test
    void theServerFolderGetsNoFileOfTheModsOwn() throws IOException {
        pack("pvp=false\n");
        run();
        restart();
        restart();
        List<String> names;
        try (Stream<Path> files = Files.list(dir)) {
            names = files.map(p -> p.getFileName().toString()).sorted().collect(Collectors.toList());
        }
        assertEquals(Arrays.asList("config", "server.properties"), names);
    }

    @Test
    void aKnownWorldTypeKeepsTheWrittenLines() throws IOException {
        Files.write(serverProperties, "motd=Old\n".getBytes(StandardCharsets.ISO_8859_1));
        pack("motd=Pack Server\n");
        player("level-type=RTG\n");
        run().checkWorldType(knowing("default", "flat", "rtg"));
        assertNull(blocker.stoppedWith);
        assertEquals("RTG", readServerProperties().getProperty("level-type"));
        assertEquals("Pack Server", readServerProperties().getProperty("motd"));
    }

    @Test
    void anUnknownWorldTypeRestoresTheFileAndStops() throws IOException {
        byte[] before = "motd=Old\nlevel-type=DEFAULT\n".getBytes(StandardCharsets.ISO_8859_1);
        Files.write(serverProperties, before);
        pack("motd=Pack Server\nlevel-type=NOSUCH\n");
        ServerLinePhase phase = run();
        assertEquals("NOSUCH", readServerProperties().getProperty("level-type"));
        phase.checkWorldType(knowing("default", "flat"));
        assertArrayEquals(before, Files.readAllBytes(serverProperties));
        assertEquals(1, blocker.stoppedWith.size());
        assertTrue(blocker.stoppedWith.get(0).message().startsWith("pack.properties line 2: level-type=NOSUCH: no installed mod"),
                blocker.stoppedWith.get(0).message());
        assertEquals("server lines: 0 applied, 0 overridden by the pack file, 2 not applied, 1 error",
                report.summaryLines(SettingCategory.SERVER_LINE).get(0));
    }

    @Test
    void anUnknownWorldTypeInAnOverriddenPlayerLineStillStops() throws IOException {
        pack("level-type=FLAT\n");
        player("level-type=NOSUCH\n");
        run().checkWorldType(knowing("default", "flat"));
        assertEquals(1, blocker.stoppedWith.size());
        assertTrue(blocker.stoppedWith.get(0).message().startsWith("player.properties line 1: level-type=NOSUCH"), blocker.stoppedWith.get(0).message());
        assertFalse(Files.exists(serverProperties));
    }

    @Test
    void anUnknownWorldTypeAlreadyInTheFileIsRestoredOverVanillasOwnResave() throws IOException {
        byte[] before = "motd=Old\nlevel-type=NOSUCH\n".getBytes(StandardCharsets.ISO_8859_1);
        Files.write(serverProperties, before);
        pack("level-type=NOSUCH\n");
        ServerLinePhase phase = run();
        // Vanilla reads the file after pre-initialization and saves it again with its own defaults.
        Files.write(serverProperties, "#Minecraft server properties\nmotd=Old\nlevel-type=NOSUCH\npvp=true\n".getBytes(StandardCharsets.ISO_8859_1));
        phase.checkWorldType(knowing("default"));
        assertArrayEquals(before, Files.readAllBytes(serverProperties));
        assertEquals(1, blocker.stoppedWith.size());
    }

    @Test
    void aFailingRegistryStillStopsAndRestores() throws IOException {
        byte[] before = "motd=Old\n".getBytes(StandardCharsets.ISO_8859_1);
        Files.write(serverProperties, before);
        pack("level-type=RTG\n");
        ServerLinePhase phase = run();
        phase.checkWorldType(new WorldTypeLookup() {
            @Override
            public boolean isKnown(String name) {
                throw new NoSuchMethodError("client-only method");
            }

            @Override
            public List<String> knownNames() {
                return Arrays.asList();
            }
        });
        assertArrayEquals(before, Files.readAllBytes(serverProperties));
        assertTrue(blocker.stoppedWith.get(0).message().contains("could not be checked"), blocker.stoppedWith.get(0).message());
    }

    @Test
    void anUnknownWorldTypeOnANewServerRemovesTheFileItCreated() throws IOException {
        pack("level-type=NOSUCH\n");
        run().checkWorldType(knowing("default"));
        assertFalse(Files.exists(serverProperties));
        assertEquals(1, blocker.stoppedWith.size());
    }

    @Test
    void withoutALevelTypeEntryNothingIsChecked() throws IOException {
        pack("motd=Pack Server\n");
        run().checkWorldType(knowing());
        assertNull(blocker.stoppedWith);
    }

    @Test
    void createsServerPropertiesWhenTheServerHasNoneYet() throws IOException {
        pack("motd=Pack Server\n");
        run();
        assertEquals("Pack Server", readServerProperties().getProperty("motd"));
    }

    @Test
    void writesNothingAndStopsWhenAnyEntryIsInvalid() throws IOException {
        byte[] before = "motd=Old\n".getBytes(StandardCharsets.ISO_8859_1);
        Files.write(serverProperties, before);
        pack("motd=Pack Server\ndifficulty=hardd\n");
        run();
        assertArrayEquals(before, Files.readAllBytes(serverProperties));
        assertEquals(1, blocker.stoppedWith.size());
        assertTrue(blocker.stoppedWith.get(0).message().startsWith("pack.properties line 2: difficulty=hardd: "));
    }

    @Test
    void doesNotRewriteAFileThatAlreadyHasTheValues() throws IOException {
        byte[] before = "# admin note\nmotd=Pack Server\n".getBytes(StandardCharsets.ISO_8859_1);
        Files.write(serverProperties, before);
        pack("motd=Pack Server\n");
        run();
        assertArrayEquals(before, Files.readAllBytes(serverProperties));
    }

    @Test
    void leavesARemovedEntrysValueInPlace() throws IOException {
        pack("pvp=false\n");
        run();
        pack("# removed\n");
        restart();
        assertEquals("false", readServerProperties().getProperty("pvp"));
    }

    @Test
    void aMalformedEscapeInServerPropertiesStopsWithAProblemInsteadOfThrowing() throws IOException {
        byte[] before = "motd=C:\\users\\ultra\n".getBytes(StandardCharsets.ISO_8859_1);
        Files.write(serverProperties, before);
        pack("pvp=false\n");
        run();
        assertEquals(1, blocker.stoppedWith.size());
        assertTrue(blocker.stoppedWith.get(0).message().contains("malformed escape"), blocker.stoppedWith.get(0).message());
        assertArrayEquals(before, Files.readAllBytes(serverProperties));
    }

    @Test
    void aFailedWriteStopsWithAProblemAndLeavesTheFileAsItWas() throws IOException {
        byte[] before = "motd=Old\n".getBytes(StandardCharsets.ISO_8859_1);
        Files.write(serverProperties, before);
        pack("motd=New\n");
        assertTrue(serverProperties.toFile().setReadOnly());
        try {
            run();
        } finally {
            assertTrue(serverProperties.toFile().setWritable(true));
        }
        assertEquals(1, blocker.stoppedWith.size());
        assertTrue(blocker.stoppedWith.get(0).message().contains("could not be written"), blocker.stoppedWith.get(0).message());
        assertArrayEquals(before, Files.readAllBytes(serverProperties));
    }

    @Test
    void restoreUndoesTheWriteExactly() throws IOException {
        byte[] before = "motd=Old\n".getBytes(StandardCharsets.ISO_8859_1);
        Files.write(serverProperties, before);
        ServerPropertiesFile file = ServerPropertiesFile.open(serverProperties);
        file.set("motd", "New");
        file.save();
        file.restore();
        assertArrayEquals(before, Files.readAllBytes(serverProperties));
    }

    @Test
    void restoreRemovesAFileThatDidNotExist() throws IOException {
        ServerPropertiesFile file = ServerPropertiesFile.open(serverProperties);
        file.set("motd", "New");
        file.save();
        file.restore();
        assertFalse(Files.exists(serverProperties));
    }
}
