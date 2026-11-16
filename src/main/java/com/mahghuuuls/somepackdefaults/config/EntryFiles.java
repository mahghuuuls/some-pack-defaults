package com.mahghuuuls.somepackdefaults.config;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * The mod's files in {@code config/somepackdefaults/}: {@code pack.properties} and
 * {@code player.properties} hold the entries; {@code suggestions.properties} is read only to make
 * a missing player file. Each line is read with the escape rules of {@code server.properties}, so
 * a line copied from a real server file means the same here. Missing files are created; an
 * existing file is never written.
 */
public final class EntryFiles {

    public static final String PACK_FILE = Source.PACK.fileName();
    public static final String PLAYER_FILE = Source.PLAYER.fileName();
    public static final String SUGGESTIONS_FILE = "suggestions.properties";
    static final String PLAYER_HEADER = "player-header.properties";

    private static final String TEMPLATE_FOLDER = "/assets/somepackdefaults/templates/";

    private final Path folder;
    private final NameKinds kinds;

    public EntryFiles(Path folder, NameKinds kinds) {
        this.folder = folder;
        this.kinds = kinds;
    }

    /** The folder of the mod's files inside the game's or server's {@code config} folder. */
    public static EntryFiles inConfigFolder(Path configFolder, NameKinds kinds) {
        return new EntryFiles(configFolder.resolve("somepackdefaults"), kinds);
    }

    public Path folder() {
        return folder;
    }

    /**
     * Creates a missing {@code pack.properties} or {@code suggestions.properties} from its template,
     * then a missing {@code player.properties} from the player header and every line of
     * {@code suggestions.properties} except lines that start with {@code ##}, which are notes for
     * the pack maker. Existing files are left untouched.
     */
    public FileCreation createMissingFiles() throws IOException {
        Files.createDirectories(folder);
        List<String> created = new ArrayList<String>();
        createFromTemplate(PACK_FILE, created);
        boolean packSuggestions = Files.exists(folder.resolve(SUGGESTIONS_FILE));
        createFromTemplate(SUGGESTIONS_FILE, created);
        Path player = folder.resolve(PLAYER_FILE);
        if (!Files.exists(player)) {
            ByteArrayOutputStream text = new ByteArrayOutputStream();
            text.write(template(PLAYER_HEADER));
            text.write(withoutPackMakerNotes(Files.readAllBytes(folder.resolve(SUGGESTIONS_FILE))));
            Files.write(player, text.toByteArray(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            created.add(PLAYER_FILE);
        }
        return new FileCreation(created, packSuggestions);
    }

    private void createFromTemplate(String fileName, List<String> created) throws IOException {
        Path file = folder.resolve(fileName);
        if (!Files.exists(file)) {
            Files.write(file, template(fileName), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            created.add(fileName);
        }
    }

    private static byte[] template(String fileName) throws IOException {
        try (InputStream in = EntryFiles.class.getResourceAsStream(TEMPLATE_FOLDER + fileName)) {
            if (in == null) {
                throw new IOException("missing template " + fileName + " in the mod jar");
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) > 0) {
                bytes.write(buffer, 0, read);
            }
            return bytes.toByteArray();
        }
    }

    /**
     * The suggestions as bytes, without a leading byte order mark and without each line whose first
     * non-blank characters are {@code ##}. Line ends and every other byte are kept as they are, so
     * the copy reads exactly like the suggestions whatever their encoding. Package-private for tests.
     */
    static byte[] withoutPackMakerNotes(byte[] suggestions) {
        int start = 0;
        if (suggestions.length >= 3 && (suggestions[0] & 0xFF) == 0xEF && (suggestions[1] & 0xFF) == 0xBB && (suggestions[2] & 0xFF) == 0xBF) {
            start = 3;
        }
        ByteArrayOutputStream kept = new ByteArrayOutputStream();
        while (start < suggestions.length) {
            int end = start;
            while (end < suggestions.length && suggestions[end] != '\n') {
                end++;
            }
            int next = end < suggestions.length ? end + 1 : end;
            int first = start;
            while (first < end && (suggestions[first] == ' ' || suggestions[first] == '\t' || suggestions[first] == '\f')) {
                first++;
            }
            boolean note = first + 1 < end && suggestions[first] == '#' && suggestions[first + 1] == '#';
            if (!note) {
                kept.write(suggestions, start, next - start);
            }
            start = next;
        }
        return kept.toByteArray();
    }

    /** Reads the pack file, then the player file; a missing file has no entries. */
    public EntrySet read() throws IOException {
        List<Entry> entries = new ArrayList<Entry>();
        List<MalformedLine> malformed = new ArrayList<MalformedLine>();
        for (Source source : Source.values()) {
            Path file = folder.resolve(source.fileName());
            if (Files.exists(file)) {
                parse(source, new String(Files.readAllBytes(file), StandardCharsets.UTF_8), kinds, entries, malformed);
            }
        }
        return new EntrySet(entries, malformed);
    }

    /** Parses one file's text. Package-private for tests. */
    static void parse(Source source, String text, NameKinds kinds, List<Entry> entries, List<MalformedLine> malformed) throws IOException {
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
                addEntry(source, logical.toString(), logicalStart, kinds, entries, malformed);
                logical = null;
            }
        }
        if (logical != null) {
            addEntry(source, logical.toString(), logicalStart, kinds, entries, malformed);
        }
    }

    private static void addEntry(Source source, String logical, int lineNumber, NameKinds kinds, List<Entry> entries, List<MalformedLine> malformed)
            throws IOException {
        Properties one = new Properties();
        try {
            one.load(new StringReader(logical));
        } catch (IllegalArgumentException e) {
            // Properties refuses a backslash-u that is not followed by four hexadecimal digits.
            malformed.add(new MalformedLine(source, lineNumber, logical.trim(), "has a \\u escape without four hexadecimal digits"));
            return;
        }
        if (one.size() != 1) {
            malformed.add(new MalformedLine(source, lineNumber, logical.trim(), "not a name=value line"));
            return;
        }
        String name = one.stringPropertyNames().iterator().next();
        if (name.isEmpty()) {
            malformed.add(new MalformedLine(source, lineNumber, logical.trim(), "not a name=value line"));
            return;
        }
        SettingCategory category = kinds.isServerLine(name) ? SettingCategory.SERVER_LINE : SettingCategory.GAMERULE;
        entries.add(new Entry(category, source, name, one.getProperty(name), lineNumber));
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
