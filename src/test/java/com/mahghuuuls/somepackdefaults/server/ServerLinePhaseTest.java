package com.mahghuuuls.somepackdefaults.server;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mahghuuuls.somepackdefaults.config.EntryFiles;
import com.mahghuuuls.somepackdefaults.config.Mode;
import com.mahghuuuls.somepackdefaults.config.SettingCategory;
import com.mahghuuuls.somepackdefaults.report.StartBlocker;
import com.mahghuuuls.somepackdefaults.report.StartReport;
import com.mahghuuuls.somepackdefaults.rules.GameruleLookup;
import com.mahghuuuls.somepackdefaults.rules.Problem;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

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

    @BeforeEach
    void setUp() throws IOException {
        config = dir.resolve("config").resolve("somepackdefaults");
        serverProperties = dir.resolve("server.properties");
        new EntryFiles(config).createMissingFiles();
        report = new StartReport();
        blocker = new RecordingBlocker();
    }

    private void entries(String file, String text) throws IOException {
        Files.write(config.resolve(file), text.getBytes(StandardCharsets.UTF_8));
    }

    private ServerLinePhase run() {
        WorldFolders worlds = new WorldFolders() {
            @Override
            public boolean hasLevelDat(String levelName) {
                return Files.isRegularFile(dir.resolve(levelName).resolve("level.dat"));
            }

            @Override
            public String startOptionWorldName() {
                return startOptionWorld;
            }
        };
        ServerLinePhase phase = new ServerLinePhase(serverProperties, report, blocker, LogManager.getLogger("test"), VANILLA_GAMERULES,
                worlds, "9.9.9");
        phase.apply(new EntryFiles(config));
        return phase;
    }

    /** The world name a {@code --world} start option gave, or null. */
    private String startOptionWorld;

    @Test
    void theWorldOfTheWorldStartOptionCounts() throws IOException {
        Files.write(serverProperties, "level-name=world\nmotd=Admin Server\n".getBytes(StandardCharsets.ISO_8859_1));
        world("myworld");
        startOptionWorld = "myworld";
        entries("server-default.properties", "motd=Pack Server\n");
        run();
        assertEquals("Admin Server", readServerProperties().getProperty("motd"));
        assertDefaultSkipped("the world already exists");
    }

    @Test
    void aFailedLevelTypeCheckLeavesNoRecordPending() throws IOException {
        entries("server-default.properties", "level-type=NOSUCH\n");
        ServerLinePhase phase = run();
        phase.checkWorldType(knowing("default"));
        phase.onFirstTickSucceeded();
        assertFalse(Files.exists(record()));
    }

    private Path record() {
        return dir.resolve("somepackdefaults-setup.json");
    }

    private void world(String name) throws IOException {
        Files.createDirectories(dir.resolve(name));
        Files.write(dir.resolve(name).resolve("level.dat"), new byte[] {1});
    }

    private void existingRecord() throws IOException {
        Files.write(record(), "{\"format\":1}\n".getBytes(StandardCharsets.UTF_8));
    }

    private List<StartReport.Status> serverLineStatuses() {
        List<StartReport.Status> result = new ArrayList<StartReport.Status>();
        for (StartReport.Status status : report.statuses()) {
            if (status.entry().category() == SettingCategory.SERVER_LINE) {
                result.add(status);
            }
        }
        return result;
    }

    private void assertDefaultSkipped(String reason) {
        boolean found = false;
        for (StartReport.Status status : serverLineStatuses()) {
            if (status.entry().mode() == Mode.DEFAULT) {
                assertEquals(StartReport.Outcome.SKIPPED, status.outcome(), status.entry().text());
                assertEquals(reason, status.reason());
                found = true;
            }
        }
        assertTrue(found, "no Default server line in the report");
    }

    private static final GameruleLookup VANILLA_GAMERULES = new GameruleLookup() {
        @Override
        public Kind kindOf(String name) {
            return name.equals("keepInventory") ? Kind.TRUE_OR_FALSE : null;
        }
    };

    @Test
    void aBadVanillaGameruleStopsBeforeAnyServerLineIsWrittenAndBothKindsAreReported() throws IOException {
        byte[] before = "motd=Old\n".getBytes(StandardCharsets.ISO_8859_1);
        Files.write(serverProperties, before);
        entries("server-reapply.properties", "motd=Pack Server\npvp=maybe\n");
        entries("gamerules-reapply.properties", "keepInventory=yes\nmodRule=anything\n");
        run();
        assertArrayEquals(before, Files.readAllBytes(serverProperties));
        assertEquals(2, blocker.stoppedWith.size());
        assertTrue(blocker.stoppedWith.get(0).message().startsWith("server-reapply.properties line 2: pvp=maybe"), blocker.stoppedWith.get(0).message());
        assertTrue(blocker.stoppedWith.get(1).message().startsWith("gamerules-reapply.properties line 1: keepInventory=yes"), blocker.stoppedWith.get(1).message());
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
    void aKnownWorldTypeKeepsTheWrittenLines() throws IOException {
        Files.write(serverProperties, "motd=Old\n".getBytes(StandardCharsets.ISO_8859_1));
        entries("server-reapply.properties", "motd=Pack Server\nlevel-type=RTG\n");
        run().checkWorldType(knowing("default", "flat", "rtg"));
        assertNull(blocker.stoppedWith);
        assertEquals("RTG", readServerProperties().getProperty("level-type"));
        assertEquals("Pack Server", readServerProperties().getProperty("motd"));
    }

    @Test
    void anUnknownWorldTypeRestoresTheFileAndStops() throws IOException {
        byte[] before = "motd=Old\nlevel-type=DEFAULT\n".getBytes(StandardCharsets.ISO_8859_1);
        Files.write(serverProperties, before);
        entries("server-reapply.properties", "motd=Pack Server\nlevel-type=NOSUCH\n");
        ServerLinePhase phase = run();
        assertEquals("NOSUCH", readServerProperties().getProperty("level-type"));
        phase.checkWorldType(knowing("default", "flat"));
        assertArrayEquals(before, Files.readAllBytes(serverProperties));
        assertEquals(1, blocker.stoppedWith.size());
        assertTrue(blocker.stoppedWith.get(0).message().startsWith("server-reapply.properties line 2: level-type=NOSUCH: no installed mod"),
                blocker.stoppedWith.get(0).message());
        assertEquals("server lines: 0 applied, 0 skipped, 2 not applied, 1 error", report.summaryLines(SettingCategory.SERVER_LINE).get(0));
    }

    @Test
    void anUnknownWorldTypeAlreadyInTheFileIsRestoredOverVanillasOwnResave() throws IOException {
        byte[] before = "motd=Old\nlevel-type=NOSUCH\n".getBytes(StandardCharsets.ISO_8859_1);
        Files.write(serverProperties, before);
        entries("server-reapply.properties", "level-type=NOSUCH\n");
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
        entries("server-reapply.properties", "level-type=RTG\n");
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
        entries("server-reapply.properties", "level-type=NOSUCH\n");
        run().checkWorldType(knowing("default"));
        assertFalse(Files.exists(serverProperties));
        assertEquals(1, blocker.stoppedWith.size());
    }

    @Test
    void withoutALevelTypeEntryNothingIsChecked() throws IOException {
        entries("server-reapply.properties", "motd=Pack Server\n");
        run().checkWorldType(knowing());
        assertNull(blocker.stoppedWith);
    }

    private Properties readServerProperties() throws IOException {
        Properties properties = new Properties();
        properties.load(new ByteArrayInputStream(Files.readAllBytes(serverProperties)));
        return properties;
    }

    @Test
    void writesReapplyLinesAndKeepsOtherLines() throws IOException {
        Files.write(serverProperties, "view-distance=6\nmotd=Old\n".getBytes(StandardCharsets.ISO_8859_1));
        entries("server-reapply.properties", "motd=Pack Server\ngamemode=2\n");
        run();
        Properties written = readServerProperties();
        assertEquals("Pack Server", written.getProperty("motd"));
        assertEquals("2", written.getProperty("gamemode"));
        assertEquals("6", written.getProperty("view-distance"));
        assertNull(blocker.stoppedWith);
        assertTrue(report.summaryLines(SettingCategory.SERVER_LINE).get(0).startsWith("server lines: 2 applied"));
    }

    @Test
    void createsTheFileWhenTheServerHasNoneYet() throws IOException {
        entries("server-reapply.properties", "motd=Pack Server\n");
        run();
        assertEquals("Pack Server", readServerProperties().getProperty("motd"));
    }

    @Test
    void writesNothingAndStopsWhenAnyEntryIsInvalid() throws IOException {
        byte[] before = "motd=Old\n".getBytes(StandardCharsets.ISO_8859_1);
        Files.write(serverProperties, before);
        entries("server-reapply.properties", "motd=Pack Server\ndifficulty=hardd\n");
        run();
        assertArrayEquals(before, Files.readAllBytes(serverProperties));
        assertEquals(1, blocker.stoppedWith.size());
        assertTrue(blocker.stoppedWith.get(0).message().startsWith("server-reapply.properties line 2: difficulty=hardd: "));
    }

    @Test
    void doesNotRewriteAFileThatAlreadyHasTheValues() throws IOException {
        byte[] before = "# admin note\nmotd=Pack Server\n".getBytes(StandardCharsets.ISO_8859_1);
        Files.write(serverProperties, before);
        entries("server-reapply.properties", "motd=Pack Server\n");
        run();
        assertArrayEquals(before, Files.readAllBytes(serverProperties));
    }

    @Test
    void leavesARemovedEntrysValueInPlace() throws IOException {
        entries("server-reapply.properties", "pvp=false\n");
        run();
        entries("server-reapply.properties", "# removed\n");
        report = new StartReport();
        run();
        assertEquals("false", readServerProperties().getProperty("pvp"));
    }

    @Test
    void aNewServerGetsDefaultAndReapplyLinesOnItsFirstStartAndTheRecordAfterTheFirstTick() throws IOException {
        entries("server-default.properties", "motd=Pack Server\ngamemode=2\n");
        entries("server-reapply.properties", "pvp=false\n");
        ServerLinePhase phase = run();
        Properties written = readServerProperties();
        assertEquals("Pack Server", written.getProperty("motd"));
        assertEquals("2", written.getProperty("gamemode"));
        assertEquals("false", written.getProperty("pvp"));
        assertTrue(report.summaryLines(SettingCategory.SERVER_LINE).get(0).startsWith("server lines: 3 applied, 0 skipped"));
        assertFalse(Files.exists(record()), "the record waits for the first tick");
        phase.onFirstTickSucceeded();
        JsonObject json = new JsonParser().parse(new String(Files.readAllBytes(record()), StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(1, json.get("format").getAsInt());
        assertEquals("9.9.9", json.get("modVersion").getAsString());
        Instant.parse(json.get("createdAt").getAsString());
        assertEquals(3, json.size());
    }

    @Test
    void anAdminChangeToADefaultLineStaysAfterARestart() throws IOException {
        entries("server-default.properties", "motd=Pack Server\n");
        run().onFirstTickSucceeded();
        Files.write(serverProperties, "motd=Admin Server\n".getBytes(StandardCharsets.ISO_8859_1));
        report = new StartReport();
        run();
        assertEquals("Admin Server", readServerProperties().getProperty("motd"));
        assertDefaultSkipped("the setup already exists");
    }

    @Test
    void aShippedServerPropertiesWithoutAWorldOrRecordIsInitialSetup() throws IOException {
        Files.write(serverProperties, "motd=Shipped\nlevel-name=world\n".getBytes(StandardCharsets.ISO_8859_1));
        entries("server-default.properties", "motd=Pack Server\n");
        run();
        assertEquals("Pack Server", readServerProperties().getProperty("motd"));
    }

    @Test
    void anExistingWorldWithoutARecordSkipsDefaultLinesAndGetsTheRecord() throws IOException {
        Files.write(serverProperties, "motd=Admin Server\n".getBytes(StandardCharsets.ISO_8859_1));
        world("world");
        entries("server-default.properties", "motd=Pack Server\n");
        run().onFirstTickSucceeded();
        assertEquals("Admin Server", readServerProperties().getProperty("motd"));
        assertDefaultSkipped("the world already exists");
        assertTrue(Files.exists(record()));
    }

    @Test
    void aRecordWithADeletedWorldIsNotInitialSetup() throws IOException {
        existingRecord();
        entries("server-default.properties", "motd=Pack Server\n");
        run();
        assertFalse(Files.exists(serverProperties) && readServerProperties().containsKey("motd"));
        assertDefaultSkipped("the setup already exists");
    }

    @Test
    void anEmptyWorldFolderWithoutLevelDatIsInitialSetup() throws IOException {
        Files.createDirectories(dir.resolve("world").resolve("region"));
        entries("server-default.properties", "motd=Pack Server\n");
        run();
        assertEquals("Pack Server", readServerProperties().getProperty("motd"));
    }

    @Test
    void aPackNamingAnotherWorldDoesNotMakeAnOldServerNew() throws IOException {
        Files.write(serverProperties, "level-name=world\nmotd=Admin Server\n".getBytes(StandardCharsets.ISO_8859_1));
        world("world");
        entries("server-default.properties", "level-name=packworld\nmotd=Pack Server\n");
        run();
        Properties written = readServerProperties();
        assertEquals("world", written.getProperty("level-name"));
        assertEquals("Admin Server", written.getProperty("motd"));
        assertDefaultSkipped("the world already exists");
    }

    @Test
    void theWorldOfAReapplyLevelNameCountsToo() throws IOException {
        world("packworld");
        entries("server-default.properties", "motd=Pack Server\n");
        entries("server-reapply.properties", "level-name=packworld\n");
        run();
        assertEquals("packworld", readServerProperties().getProperty("level-name"));
        assertDefaultSkipped("the world already exists");
    }

    @Test
    void theWorldOfTheDefaultLevelNameCountsOnANewFolder() throws IOException {
        world("packworld");
        entries("server-default.properties", "level-name=packworld\nmotd=Pack Server\n");
        run();
        assertDefaultSkipped("the world already exists");
    }

    @Test
    void aPackUpdateReachesExistingServersOnlyThroughTheReapplyFile() throws IOException {
        existingRecord();
        Files.write(serverProperties, "allow-flight=false\n".getBytes(StandardCharsets.ISO_8859_1));
        entries("server-default.properties", "allow-flight=true\n");
        run();
        assertEquals("false", readServerProperties().getProperty("allow-flight"));
        entries("server-default.properties", "# moved\n");
        entries("server-reapply.properties", "allow-flight=true\n");
        report = new StartReport();
        run();
        assertEquals("true", readServerProperties().getProperty("allow-flight"));
    }

    @Test
    void aStoppedStartWritesNoRecord() throws IOException {
        entries("server-default.properties", "difficulty=hardd\n");
        run().onFirstTickSucceeded();
        assertFalse(Files.exists(record()));
    }

    @Test
    void anExistingRecordIsNeverReplaced() throws IOException {
        existingRecord();
        byte[] before = Files.readAllBytes(record());
        run().onFirstTickSucceeded();
        assertFalse(SetupRecord.besides(serverProperties).writeIfAbsent(Instant.now(), "1.0.0"));
        assertArrayEquals(before, Files.readAllBytes(record()));
    }

    @Test
    void aDefaultLevelTypeWrittenAtInitialSetupIsChecked() throws IOException {
        entries("server-default.properties", "level-type=NOSUCH\n");
        run().checkWorldType(knowing("default"));
        assertEquals(1, blocker.stoppedWith.size());
        assertFalse(Files.exists(serverProperties));
    }

    @Test
    void aSkippedDefaultLevelTypeIsNotChecked() throws IOException {
        existingRecord();
        entries("server-default.properties", "level-type=NOSUCH\n");
        run().checkWorldType(knowing("default"));
        assertNull(blocker.stoppedWith);
    }

    @Test
    void aMalformedEscapeInServerPropertiesStopsWithAProblemInsteadOfThrowing() throws IOException {
        byte[] before = "motd=C:\\users\\ultra\n".getBytes(StandardCharsets.ISO_8859_1);
        Files.write(serverProperties, before);
        entries("server-reapply.properties", "pvp=false\n");
        run();
        assertEquals(1, blocker.stoppedWith.size());
        assertTrue(blocker.stoppedWith.get(0).message().contains("malformed escape"), blocker.stoppedWith.get(0).message());
        assertArrayEquals(before, Files.readAllBytes(serverProperties));
    }

    @Test
    void aFailedWriteStopsWithAProblemAndLeavesTheFileAsItWas() throws IOException {
        byte[] before = "motd=Old\n".getBytes(StandardCharsets.ISO_8859_1);
        Files.write(serverProperties, before);
        entries("server-reapply.properties", "motd=New\n");
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
