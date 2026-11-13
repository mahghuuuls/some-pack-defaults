package com.mahghuuuls.somepackdefaults;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fails when a source file breaks the package rules: entry reading and checking stay free of
 * Minecraft and Forge so they can be tested directly (also by fully qualified names), nothing loads
 * client classes, only the stop implementation in report uses Forge, the root class reaches the mod
 * only through the three lifecycle packages, and those packages do not depend on each other.
 */
class ArchitectureRulesTest {

    private static final Path ROOT = Paths.get("src", "main", "java", "com", "mahghuuuls", "somepackdefaults");

    private static List<Path> sources() throws IOException {
        assertTrue(Files.isDirectory(ROOT), "run from the project folder; missing " + ROOT.toAbsolutePath());
        try (Stream<Path> walk = Files.walk(ROOT)) {
            return walk.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
        }
    }

    private static String packageOf(Path source) {
        Path relative = ROOT.relativize(source.getParent());
        return relative.toString().isEmpty() ? "" : relative.getName(0).toString();
    }

    private static List<String> imports(Path source) throws IOException {
        List<String> result = new ArrayList<String>();
        for (String line : new String(Files.readAllBytes(source), StandardCharsets.UTF_8).split("\n")) {
            line = line.trim();
            if (line.startsWith("import ")) {
                result.add(line.substring(7).replace(";", "").replace("static ", "").trim());
            }
        }
        return result;
    }

    /** The source without comments, so a qualified name in code is found and one in a comment is not. */
    private static String code(Path source) throws IOException {
        String text = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
        return text.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\n]*", "");
    }

    @Test
    void packageRulesHold() throws IOException {
        List<String> violations = new ArrayList<String>();
        List<Path> files = sources();
        for (Path source : files) {
            String pkg = packageOf(source);
            if ((pkg.equals("config") || pkg.equals("rules")) && code(source).contains("net.minecraft")) {
                violations.add(source + " (" + pkg + ") refers to net.minecraft or net.minecraftforge");
            }
            for (String imported : imports(source)) {
                if (pkg.equals("report") && !source.getFileName().toString().equals("ForgeStartBlocker.java")
                        && (imported.startsWith("net.minecraft") || imported.startsWith("net.minecraftforge"))) {
                    violations.add(source + " (report) imports " + imported + "; only ForgeStartBlocker may use Forge");
                }
                if (pkg.isEmpty() && imported.startsWith("com.mahghuuuls.somepackdefaults.")
                        && !imported.startsWith("com.mahghuuuls.somepackdefaults.server.")
                        && !imported.startsWith("com.mahghuuuls.somepackdefaults.world.")
                        && !imported.startsWith("com.mahghuuuls.somepackdefaults.command.")) {
                    violations.add(source + " (root) imports " + imported);
                }
                if (imported.startsWith("net.minecraft.client")) {
                    violations.add(source + " imports client class " + imported);
                }
                if ((pkg.equals("config") || pkg.equals("rules"))
                        && (imported.startsWith("net.minecraft") || imported.startsWith("net.minecraftforge"))) {
                    violations.add(source + " (" + pkg + ") imports " + imported);
                }
                if (pkg.equals("rules") && imported.startsWith("com.mahghuuuls.somepackdefaults.")
                        && !imported.startsWith("com.mahghuuuls.somepackdefaults.config.") && !imported.startsWith("com.mahghuuuls.somepackdefaults.rules.")) {
                    violations.add(source + " (rules) imports " + imported);
                }
                for (String lifecycle : new String[] {"server", "world", "command"}) {
                    if (!pkg.equals(lifecycle) && (pkg.equals("server") || pkg.equals("world") || pkg.equals("command"))
                            && imported.startsWith("com.mahghuuuls.somepackdefaults." + lifecycle + ".")) {
                        violations.add(source + " (" + pkg + ") imports " + imported);
                    }
                }
            }
        }
        assertTrue(files.size() > 5, "too few sources found: " + files.size());
        assertTrue(violations.isEmpty(), String.join("\n", violations));
    }
}
