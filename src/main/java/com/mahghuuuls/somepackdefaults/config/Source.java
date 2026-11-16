package com.mahghuuuls.somepackdefaults.config;

/**
 * The file an entry came from. The pack maker owns the pack file and ships it with the pack; the
 * player or server admin owns the player file, which the pack never ships.
 */
public enum Source {
    PACK("pack file", "pack.properties"),
    PLAYER("player file", "player.properties");

    private final String label;
    private final String fileName;

    Source(String label, String fileName) {
        this.label = label;
        this.fileName = fileName;
    }

    /** Words used in log and inspection output. */
    public String label() {
        return label;
    }

    public String fileName() {
        return fileName;
    }
}
