package com.mahghuuuls.somepackdefaults.server;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * {@code server.properties} read and written the way vanilla does it ({@code java.util.Properties}
 * in ISO-8859-1, with vanilla's header comment), plus a byte snapshot of the file as it was before
 * this start's write, so a later check can undo the write exactly.
 */
final class ServerPropertiesFile {

    private static final String HEADER = "Minecraft server properties";

    private final Path path;
    private final byte[] snapshot;
    private final Properties properties = new Properties();
    private boolean changed;

    private ServerPropertiesFile(Path path, byte[] snapshot) throws IOException {
        this.path = path;
        this.snapshot = snapshot;
        if (snapshot != null) {
            properties.load(new ByteArrayInputStream(snapshot));
        }
    }

    /** Reads the file; a malformed backslash-u escape in it is reported as an {@link IOException}. */
    static ServerPropertiesFile open(Path path) throws IOException {
        byte[] bytes = Files.exists(path) ? Files.readAllBytes(path) : null;
        try {
            return new ServerPropertiesFile(path, bytes);
        } catch (IllegalArgumentException e) {
            throw new IOException("it has a malformed escape (" + e.getMessage() + ")", e);
        }
    }

    /** Sets a line; the file changes only when the value differs or the line is missing. */
    void set(String name, String value) {
        if (!value.equals(properties.getProperty(name))) {
            properties.setProperty(name, value);
            changed = true;
        }
    }

    /** Writes the file when a line changed. */
    void save() throws IOException {
        if (!changed) {
            return;
        }
        try (OutputStream out = Files.newOutputStream(path)) {
            properties.store(out, HEADER);
        }
    }

    /**
     * Puts the file back exactly as it was when it was opened, or removes it when there was none.
     * This writes even when this class changed nothing: vanilla re-saves the file itself between
     * pre-initialization and post-initialization.
     */
    void restore() throws IOException {
        if (snapshot == null) {
            Files.deleteIfExists(path);
        } else {
            Files.write(path, snapshot);
        }
        changed = false;
    }
}
