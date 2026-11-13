package com.mahghuuuls.somepackdefaults.server;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

/**
 * The setup record {@code somepackdefaults-setup.json} next to {@code server.properties}. Only its
 * presence is ever read; the fields say when and by which version it was written. It is never in
 * {@code config/} or the world folder, so neither a pack update nor a world reset removes it.
 */
final class SetupRecord {

    static final String FILE_NAME = "somepackdefaults-setup.json";
    static final int FORMAT = 1;

    private final Path path;

    private SetupRecord(Path path) {
        this.path = path;
    }

    static SetupRecord besides(Path serverProperties) {
        return new SetupRecord(serverProperties.resolveSibling(FILE_NAME));
    }

    Path path() {
        return path;
    }

    boolean exists() {
        return Files.exists(path);
    }

    /** Writes the record unless one exists; never replaces one. Returns whether it wrote. */
    boolean writeIfAbsent(Instant createdAt, String modVersion) throws IOException {
        JsonObject json = new JsonObject();
        json.addProperty("format", FORMAT);
        json.addProperty("createdAt", createdAt.toString());
        json.addProperty("modVersion", modVersion);
        byte[] bytes = (new GsonBuilder().setPrettyPrinting().create().toJson(json) + "\n").getBytes(StandardCharsets.UTF_8);
        try {
            Files.write(path, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            return true;
        } catch (FileAlreadyExistsException e) {
            return false;
        }
    }
}
