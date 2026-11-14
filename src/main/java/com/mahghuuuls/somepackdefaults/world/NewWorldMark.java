package com.mahghuuuls.somepackdefaults.world;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/**
 * The new-world mark {@code somepackdefaults-new-world.json} in a world folder. It is written only
 * when a new world's first open was stopped, so the world stays new for the mod, and removed once
 * that world's Default gamerules are applied. Only its presence is read.
 */
final class NewWorldMark {

    static final String FILE_NAME = "somepackdefaults-new-world.json";

    private final Path path;

    private NewWorldMark(Path path) {
        this.path = path;
    }

    static NewWorldMark in(Path worldFolder) {
        return new NewWorldMark(worldFolder.resolve(FILE_NAME));
    }

    Path path() {
        return path;
    }

    boolean exists() {
        return Files.exists(path);
    }

    void write(Instant writtenAt) throws IOException {
        JsonObject json = new JsonObject();
        json.addProperty("format", 1);
        json.addProperty("writtenAt", writtenAt.toString());
        Files.write(path, (new GsonBuilder().setPrettyPrinting().create().toJson(json) + "\n").getBytes(StandardCharsets.UTF_8));
    }

    void remove() throws IOException {
        Files.deleteIfExists(path);
    }
}
