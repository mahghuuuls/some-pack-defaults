package com.mahghuuuls.somepackdefaults.config;

/** One {@code name=value} line of the pack file or the player file, with where it was read. */
public final class Entry {

    private final SettingCategory category;
    private final Source source;
    private final String name;
    private final String value;
    private final int lineNumber;

    public Entry(SettingCategory category, Source source, String name, String value, int lineNumber) {
        this.category = category;
        this.source = source;
        this.name = name;
        this.value = value;
        this.lineNumber = lineNumber;
    }

    public SettingCategory category() {
        return category;
    }

    public Source source() {
        return source;
    }

    public String name() {
        return name;
    }

    public String value() {
        return value;
    }

    /** The file this entry came from. */
    public String fileName() {
        return source.fileName();
    }

    /** The first physical line of the entry in its file, counted from 1. */
    public int lineNumber() {
        return lineNumber;
    }

    /** The entry as it was written, for messages. */
    public String text() {
        return name + "=" + value;
    }
}
