package com.mahghuuuls.somepackdefaults.config;

/** The two kinds of setting an entry can name. Both kinds can be in each file. */
public enum SettingCategory {
    SERVER_LINE("server line"),
    GAMERULE("gamerule");

    private final String label;

    SettingCategory(String label) {
        this.label = label;
    }

    /** Words used in log and inspection output. */
    public String label() {
        return label;
    }
}
