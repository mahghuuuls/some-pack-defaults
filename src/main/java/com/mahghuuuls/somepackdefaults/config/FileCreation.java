package com.mahghuuuls.somepackdefaults.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** What one call to {@link EntryFiles#createMissingFiles()} created. */
public final class FileCreation {

    private final List<String> createdFiles;
    private final boolean playerFileFromPackSuggestions;

    FileCreation(List<String> createdFiles, boolean playerFileFromPackSuggestions) {
        this.createdFiles = Collections.unmodifiableList(new ArrayList<String>(createdFiles));
        this.playerFileFromPackSuggestions = playerFileFromPackSuggestions;
    }

    /** The names of the created files, in the order they were created. */
    public List<String> createdFiles() {
        return createdFiles;
    }

    public boolean createdPlayerFile() {
        return createdFiles.contains(EntryFiles.PLAYER_FILE);
    }

    /**
     * True when the player file was made from a {@code suggestions.properties} that existed before
     * this call (the pack's own); false when that file was missing and came from the template too.
     */
    public boolean playerFileFromPackSuggestions() {
        return playerFileFromPackSuggestions;
    }
}
