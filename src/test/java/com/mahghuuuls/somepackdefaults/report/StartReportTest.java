package com.mahghuuuls.somepackdefaults.report;

import com.mahghuuuls.somepackdefaults.config.EntryFiles;
import com.mahghuuuls.somepackdefaults.config.NameKinds;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The log lines that say which files were created and where a new player file came from. */
class StartReportTest {

    private static final NameKinds NO_SERVER_LINES = new NameKinds() {
        @Override
        public boolean isServerLine(String name) {
            return false;
        }
    };

    @Test
    void aFreshFolderNamesTheSuggestionsTemplateAsTheSource(@TempDir Path dir) throws IOException {
        assertEquals(Arrays.asList("created pack.properties from its template", "created suggestions.properties from its template",
                "created player.properties from the suggestions template (the pack ships no suggestions.properties)"),
                StartReport.creationLines(new EntryFiles(dir, NO_SERVER_LINES).createMissingFiles()));
    }

    @Test
    void aPackWithSuggestionsIsNamedAsTheSource(@TempDir Path dir) throws IOException {
        Files.write(dir.resolve("suggestions.properties"), "keepInventory=true\n".getBytes(StandardCharsets.UTF_8));
        Files.write(dir.resolve("pack.properties"), "\n".getBytes(StandardCharsets.UTF_8));
        assertEquals(Arrays.asList("created player.properties from the pack's suggestions.properties"),
                StartReport.creationLines(new EntryFiles(dir, NO_SERVER_LINES).createMissingFiles()));
    }

    @Test
    void nothingCreatedLogsNothing(@TempDir Path dir) throws IOException {
        new EntryFiles(dir, NO_SERVER_LINES).createMissingFiles();
        assertTrue(StartReport.creationLines(new EntryFiles(dir, NO_SERVER_LINES).createMissingFiles()).isEmpty());
    }
}
