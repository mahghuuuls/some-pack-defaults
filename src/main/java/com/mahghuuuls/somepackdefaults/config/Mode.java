package com.mahghuuuls.somepackdefaults.config;

/**
 * When an entry applies. A Default entry gives a starting value once, for a new server setup or a
 * new world. A Reapply entry is set again at every start or world open.
 */
public enum Mode {
    DEFAULT("Default", "default"),
    REAPPLY("Reapply", "reapply");

    private final String label;
    private final String fileSuffix;

    Mode(String label, String fileSuffix) {
        this.label = label;
        this.fileSuffix = fileSuffix;
    }

    public String label() {
        return label;
    }

    String fileSuffix() {
        return fileSuffix;
    }
}
