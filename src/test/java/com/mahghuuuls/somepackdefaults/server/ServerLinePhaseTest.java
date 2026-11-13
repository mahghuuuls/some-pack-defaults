package com.mahghuuuls.somepackdefaults.server;

import com.mahghuuuls.somepackdefaults.config.EntryFiles;
import com.mahghuuuls.somepackdefaults.config.SettingCategory;
import com.mahghuuuls.somepackdefaults.report.StartBlocker;
import com.mahghuuuls.somepackdefaults.report.StartReport;
import com.mahghuuuls.somepackdefaults.rules.Problem;
import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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

    private void run() {
        new ServerLinePhase(serverProperties, report, blocker, LogManager.getLogger("test")).apply(new EntryFiles(config));
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
    void reportsDefaultLinesWithoutWritingThem() throws IOException {
        entries("server-default.properties", "allow-flight=true\n");
        run();
        assertFalse(Files.exists(serverProperties) && readServerProperties().containsKey("allow-flight"));
        assertTrue(report.summaryLines(SettingCategory.SERVER_LINE).get(0).contains("1 not applied"));
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
