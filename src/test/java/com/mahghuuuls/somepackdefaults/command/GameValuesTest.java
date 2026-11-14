package com.mahghuuuls.somepackdefaults.command;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/** The inspection's file reader never writes, and tolerates a missing or unreadable file. */
class GameValuesTest {

    @TempDir
    Path dir;

    @Test
    void readingLeavesTheFileByteIdenticalEvenWithoutSpawnProtection() throws IOException {
        Path file = dir.resolve("server.properties");
        byte[] before = "#Minecraft server properties\nmotd=Old\npvp=true\n".getBytes(StandardCharsets.ISO_8859_1);
        Files.write(file, before);
        long modified = Files.getLastModifiedTime(file).toMillis();
        Properties read = GameValues.readOnly(file);
        assertEquals("Old", read.getProperty("motd"));
        assertNull(read.getProperty("spawn-protection"));
        assertArrayEquals(before, Files.readAllBytes(file));
        assertEquals(modified, Files.getLastModifiedTime(file).toMillis());
    }

    @Test
    void aMissingFileGivesNoValuesAndIsNotCreated() {
        Path file = dir.resolve("server.properties");
        assertNull(GameValues.readOnly(file));
        assertFalse(Files.exists(file));
    }

    @Test
    void aMalformedEscapeGivesNoValues() throws IOException {
        Path file = dir.resolve("server.properties");
        Files.write(file, "motd=C:\\users\\ultra\n".getBytes(StandardCharsets.ISO_8859_1));
        assertNull(GameValues.readOnly(file));
    }
}
