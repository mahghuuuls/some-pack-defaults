package com.mahghuuuls.somepackdefaults.config;

/** A non-comment line that does not form a {@code name=value} entry, kept so it can be reported. */
public final class MalformedLine {

    private final SettingCategory category;
    private final Mode mode;
    private final int lineNumber;
    private final String text;
    private final String reason;

    public MalformedLine(SettingCategory category, Mode mode, int lineNumber, String text, String reason) {
        this.category = category;
        this.mode = mode;
        this.lineNumber = lineNumber;
        this.text = text;
        this.reason = reason;
    }

    public SettingCategory category() {
        return category;
    }

    public String fileName() {
        return category.fileName(mode);
    }

    public int lineNumber() {
        return lineNumber;
    }

    public String text() {
        return text;
    }

    /** What is wrong with the line. */
    public String reason() {
        return reason;
    }
}
