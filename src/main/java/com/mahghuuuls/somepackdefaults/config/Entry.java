package com.mahghuuuls.somepackdefaults.config;

/** One {@code name=value} line of one configuration file, with where it was read. */
public final class Entry {

    private final SettingCategory category;
    private final Mode mode;
    private final String name;
    private final String value;
    private final int lineNumber;

    public Entry(SettingCategory category, Mode mode, String name, String value, int lineNumber) {
        this.category = category;
        this.mode = mode;
        this.name = name;
        this.value = value;
        this.lineNumber = lineNumber;
    }

    public SettingCategory category() {
        return category;
    }

    public Mode mode() {
        return mode;
    }

    public String name() {
        return name;
    }

    public String value() {
        return value;
    }

    /** The file this entry came from. */
    public String fileName() {
        return category.fileName(mode);
    }

    /** The first physical line of the entry in its file, counted from 1. */
    public int lineNumber() {
        return lineNumber;
    }

    /** The entry as a pack maker wrote it, for messages. */
    public String text() {
        return name + "=" + value;
    }
}
