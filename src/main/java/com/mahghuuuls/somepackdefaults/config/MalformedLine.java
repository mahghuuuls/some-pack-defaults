package com.mahghuuuls.somepackdefaults.config;

/** A non-comment line that does not form a {@code name=value} entry, kept so it can be reported. */
public final class MalformedLine {

    private final Source source;
    private final int lineNumber;
    private final String text;
    private final String reason;

    public MalformedLine(Source source, int lineNumber, String text, String reason) {
        this.source = source;
        this.lineNumber = lineNumber;
        this.text = text;
        this.reason = reason;
    }

    public String fileName() {
        return source.fileName();
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
