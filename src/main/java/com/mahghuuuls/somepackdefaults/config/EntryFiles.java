package com.mahghuuuls.somepackdefaults.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * The mod's four configuration files in {@code config/somepackdefaults/}. Each line is read with
 * the escape rules of {@code server.properties}, so a line copied from a real server file means
 * the same here. Missing files are created from templates in the jar; an existing file is never
 * written.
 */
public final class EntryFiles {

    private static final String TEMPLATE_FOLDER = "/assets/somepackdefaults/templates/";

    private final Path folder;

    public EntryFiles(Path folder) {
        this.folder = folder;
    }

    public Path folder() {
        return folder;
    }

    /** Creates each missing file from its template. Existing files are left untouched. */
    public void createMissingFiles() throws IOException {
        Files.createDirectories(folder);
        for (SettingCategory category : SettingCategory.values()) {
            for (Mode mode : Mode.values()) {
                Path file = folder.resolve(category.fileName(mode));
                if (Files.exists(file)) {
                    continue;
                }
                try (InputStream template = EntryFiles.class.getResourceAsStream(TEMPLATE_FOLDER + category.fileName(mode))) {
                    if (template == null) {
                        throw new IOException("missing template " + category.fileName(mode) + " in the mod jar");
                    }
                    Files.copy(template, file);
                }
            }
        }
    }

    /** Creates missing files, then reads all four. */
    public EntrySet read() throws IOException {
        createMissingFiles();
        List<Entry> entries = new ArrayList<Entry>();
        List<MalformedLine> malformed = new ArrayList<MalformedLine>();
        for (SettingCategory category : SettingCategory.values()) {
            for (Mode mode : Mode.values()) {
                Path file = folder.resolve(category.fileName(mode));
                String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                parse(category, mode, text, entries, malformed);
            }
        }
        return new EntrySet(entries, malformed);
    }

    /** Parses one file's text. Package-private for tests. */
    static void parse(SettingCategory category, Mode mode, String text, List<Entry> entries, List<MalformedLine> malformed) throws IOException {
        if (text.startsWith("\uFEFF")) {
            text = text.substring(1);
        }
        BufferedReader reader = new BufferedReader(new StringReader(text));
        StringBuilder logical = null;
        int logicalStart = 0;
        int lineNumber = 0;
        String line;
        while ((line = reader.readLine()) != null) {
            lineNumber++;
            if (logical == null) {
                String trimmed = stripLeading(line);
                if (trimmed.isEmpty() || trimmed.charAt(0) == '#' || trimmed.charAt(0) == '!') {
                    continue;
                }
                logical = new StringBuilder();
                logicalStart = lineNumber;
            } else {
                logical.append('\n');
            }
            logical.append(line);
            if (!continues(line)) {
                addEntry(category, mode, logical.toString(), logicalStart, entries, malformed);
                logical = null;
            }
        }
        if (logical != null) {
            addEntry(category, mode, logical.toString(), logicalStart, entries, malformed);
        }
    }

    private static void addEntry(SettingCategory category, Mode mode, String logical, int lineNumber, List<Entry> entries, List<MalformedLine> malformed) throws IOException {
        Properties one = new Properties();
        try {
            one.load(new StringReader(logical));
        } catch (IllegalArgumentException e) {
            // Properties refuses a backslash-u that is not followed by four hexadecimal digits.
            malformed.add(new MalformedLine(category, mode, lineNumber, logical.trim(), "has a \\u escape without four hexadecimal digits"));
            return;
        }
        if (one.size() != 1) {
            malformed.add(new MalformedLine(category, mode, lineNumber, logical.trim(), "not a name=value line"));
            return;
        }
        String name = one.stringPropertyNames().iterator().next();
        if (name.isEmpty()) {
            malformed.add(new MalformedLine(category, mode, lineNumber, logical.trim(), "not a name=value line"));
            return;
        }
        entries.add(new Entry(category, mode, name, one.getProperty(name), lineNumber));
    }

    /** The folder of the mod's files inside the game's or server's {@code config} folder. */
    public static EntryFiles inConfigFolder(Path configFolder) {
        return new EntryFiles(configFolder.resolve("somepackdefaults"));
    }

    /** A line continues onto the next one when it ends with an odd number of backslashes. */
    private static boolean continues(String line) {
        int count = 0;
        for (int i = line.length() - 1; i >= 0 && line.charAt(i) == '\\'; i--) {
            count++;
        }
        return count % 2 == 1;
    }

    private static String stripLeading(String line) {
        int i = 0;
        while (i < line.length() && (line.charAt(i) == ' ' || line.charAt(i) == '\t' || line.charAt(i) == '\f')) {
            i++;
        }
        return line.substring(i);
    }
}
