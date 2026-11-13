package com.mahghuuuls.somepackdefaults.config;

/** The two kinds of setting an entry can name. Each has one Default file and one Reapply file. */
public enum SettingCategory {
    SERVER_LINE("server line", "server"),
    GAMERULE("gamerule", "gamerules");

    private final String label;
    private final String filePrefix;

    SettingCategory(String label, String filePrefix) {
        this.label = label;
        this.filePrefix = filePrefix;
    }

    /** Words used in log and inspection output. */
    public String label() {
        return label;
    }

    /** The configuration file that holds this category's entries for the given mode. */
    public String fileName(Mode mode) {
        return filePrefix + "-" + mode.fileSuffix() + ".properties";
    }
}
