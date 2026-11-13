package com.mahghuuuls.somepackdefaults.rules;

/**
 * One configuration error. This is the only shape errors take, so every message names the same
 * things: the file, the line, the entry, what is wrong, and what is allowed.
 */
public final class Problem {

    private final String fileName;
    private final int lineNumber;
    private final String entryText;
    private final String problem;
    private final String allowed;

    public Problem(String fileName, int lineNumber, String entryText, String problem, String allowed) {
        this.fileName = fileName;
        this.lineNumber = lineNumber;
        this.entryText = entryText;
        this.problem = problem;
        this.allowed = allowed;
    }

    public String fileName() {
        return fileName;
    }

    public int lineNumber() {
        return lineNumber;
    }

    public String entryText() {
        return entryText;
    }

    /** Example: {@code server-reapply.properties line 4: gamemode=9: out of range. Allowed: 0 to 3.} */
    public String message() {
        StringBuilder text = new StringBuilder();
        text.append(fileName);
        if (lineNumber > 0) {
            text.append(" line ").append(lineNumber);
        }
        text.append(": ");
        if (entryText != null) {
            text.append(entryText).append(": ");
        }
        text.append(problem).append('.');
        if (allowed != null) {
            text.append(" Allowed: ").append(allowed).append('.');
        }
        return text.toString();
    }

    @Override
    public String toString() {
        return message();
    }
}
