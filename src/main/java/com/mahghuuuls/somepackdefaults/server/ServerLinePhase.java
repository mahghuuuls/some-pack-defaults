package com.mahghuuuls.somepackdefaults.server;

import com.mahghuuuls.somepackdefaults.config.Entry;
import com.mahghuuuls.somepackdefaults.config.EntryFiles;
import com.mahghuuuls.somepackdefaults.config.EntrySet;
import com.mahghuuuls.somepackdefaults.config.Mode;
import com.mahghuuuls.somepackdefaults.config.SettingCategory;
import com.mahghuuuls.somepackdefaults.report.ForgeStartBlocker;
import com.mahghuuuls.somepackdefaults.report.StartBlocker;
import com.mahghuuuls.somepackdefaults.report.StartReport;
import com.mahghuuuls.somepackdefaults.rules.EntryChecker;
import com.mahghuuuls.somepackdefaults.rules.GameruleLookup;
import com.mahghuuuls.somepackdefaults.rules.Problem;
import com.mahghuuuls.somepackdefaults.rules.ServerLineTable;
import com.mahghuuuls.somepackdefaults.rules.WorldTypeLookup;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * The server-line side of a dedicated-server start. It acts only on a dedicated server, at
 * pre-initialization, before vanilla reads {@code server.properties}, so every line it writes takes
 * effect on this same start. It checks every server-line entry first and writes nothing when any is
 * invalid. It decides whether this is initial server setup: then Default and Reapply lines are
 * written together; otherwise only Reapply lines. The instance lives for the whole start and keeps
 * the file it wrote, so a later check can undo the write, and writes the setup record once the
 * start has succeeded.
 */
public final class ServerLinePhase {

    private final Path serverProperties;
    private final StartReport report;
    private final StartBlocker blocker;
    private final Logger logger;
    /** The gamerules every vanilla world has, so their values are checked before any world loads. */
    private final GameruleLookup vanillaGamerules;
    private final WorldFolders worlds;
    private final SetupRecord record;
    private final String modVersion;

    private ServerPropertiesFile written;
    /** The {@code level-type} entry this start wrote, checked later against the registered world types. */
    private Entry writtenLevelType;
    /** True when this start found no setup record and must write one once it succeeds. */
    private boolean recordPending;

    public ServerLinePhase(Logger logger, GameruleLookup vanillaGamerules, String modVersion) {
        // Vanilla reads server.properties from the server's working folder.
        this(Paths.get("server.properties"), new StartReport(), new ForgeStartBlocker(logger), logger, vanillaGamerules,
                new MinecraftWorldFolders(), modVersion);
    }

    ServerLinePhase(Path serverProperties, StartReport report, StartBlocker blocker, Logger logger, GameruleLookup vanillaGamerules,
            WorldFolders worlds, String modVersion) {
        this.serverProperties = serverProperties;
        this.report = report;
        this.blocker = blocker;
        this.logger = logger;
        this.vanillaGamerules = vanillaGamerules;
        this.worlds = worlds;
        this.record = SetupRecord.besides(serverProperties);
        this.modVersion = modVersion;
    }

    public StartReport report() {
        return report;
    }

    public void onPreInit(FMLPreInitializationEvent event) {
        if (!event.getSide().isServer()) {
            return;
        }
        apply(EntryFiles.inConfigFolder(event.getModConfigurationDirectory().toPath()));
    }

    /**
     * Checks every entry that can be checked before a world loads (all server lines, and gamerule
     * entries that name a vanilla gamerule), then writes the server lines; or stops the server with
     * every problem found and writes nothing.
     */
    void apply(EntryFiles files) {
        EntrySet set;
        try {
            set = files.read();
        } catch (IOException e) {
            stop(Collections.singletonList(new Problem(files.folder().toString(), 0, null, "could not be read (" + e + ")", null)));
            return;
        }
        List<Problem> problems = EntryChecker.checkServerLines(set);
        List<Problem> gameruleProblems = EntryChecker.checkGamerulesBeforeLoad(set, vanillaGamerules);
        if (!problems.isEmpty() || !gameruleProblems.isEmpty()) {
            report.problems(SettingCategory.GAMERULE, gameruleProblems);
            report.log(logger, SettingCategory.GAMERULE);
            List<Problem> all = new ArrayList<Problem>(problems);
            all.addAll(gameruleProblems);
            report.problems(SettingCategory.SERVER_LINE, problems);
            report.log(logger, SettingCategory.SERVER_LINE);
            blocker.stopBeforeWorldLoad(all);
            return;
        }
        try {
            written = ServerPropertiesFile.open(serverProperties);
        } catch (IOException e) {
            stop(Collections.singletonList(new Problem(serverProperties.toString(), 0, null, "could not be read: " + e.getMessage(), null)));
            return;
        }
        SetupDecision.State setup = decideSetup(set);
        recordPending = setup != SetupDecision.State.EXISTING_SETUP;
        for (Entry entry : set.entries(SettingCategory.SERVER_LINE, Mode.DEFAULT)) {
            if (setup == SetupDecision.State.INITIAL) {
                write(entry);
            } else {
                report.skipped(entry, setup.skipReason());
            }
        }
        for (Entry entry : set.entries(SettingCategory.SERVER_LINE, Mode.REAPPLY)) {
            write(entry);
        }
        try {
            written.save();
        } catch (IOException e) {
            restore("a failed write");
            stop(Collections.singletonList(new Problem(serverProperties.toString(), 0, null, "could not be written (" + e + ")", null)));
            return;
        }
        report.log(logger, SettingCategory.SERVER_LINE);
    }

    /** Applies the setup rule to this start's facts and logs the outcome with the facts behind it. */
    private SetupDecision.State decideSetup(EntrySet set) {
        boolean recordExists = record.exists();
        Set<String> names = SetupDecision.worldNames(written.get(ServerLineTable.LEVEL_NAME),
                levelName(set, Mode.DEFAULT), levelName(set, Mode.REAPPLY), worlds.startOptionWorldName());
        String existingWorld = null;
        for (String name : names) {
            if (worlds.hasLevelDat(name)) {
                existingWorld = name;
                break;
            }
        }
        SetupDecision.State setup = SetupDecision.decide(recordExists, existingWorld != null);
        if (setup == SetupDecision.State.INITIAL) {
            logger.info(StartReport.PREFIX + "initial server setup: no setup record and no world among {}", names);
        } else if (setup == SetupDecision.State.EXISTING_SETUP) {
            logger.info(StartReport.PREFIX + "existing setup: the setup record {} exists", record.path());
        } else {
            logger.info(StartReport.PREFIX + "existing setup: the world {} already exists (it has level.dat)", existingWorld);
        }
        return setup;
    }

    private static Entry levelName(EntrySet set, Mode mode) {
        Entry found = null;
        for (Entry entry : set.entries(SettingCategory.SERVER_LINE, mode)) {
            if (entry.name().equals(ServerLineTable.LEVEL_NAME)) {
                found = entry;
            }
        }
        return found;
    }

    private void write(Entry entry) {
        written.set(entry.name(), entry.value());
        report.applied(entry);
        if (entry.name().equals(ServerLineTable.LEVEL_TYPE)) {
            writtenLevelType = entry;
        }
    }

    /**
     * Called once this start's first server tick has succeeded. When the start found no setup record,
     * writes it now, so the next start counts as an existing setup. A stopped start never gets here.
     * Does nothing in singleplayer, where this phase never ran.
     */
    public void onFirstTickSucceeded() {
        if (!recordPending) {
            return;
        }
        recordPending = false;
        try {
            if (record.writeIfAbsent(Instant.now(), modVersion)) {
                logger.info(StartReport.PREFIX + "wrote the setup record {}; later starts count as an existing setup", record.path());
            }
        } catch (IOException e) {
            logger.error(StartReport.PREFIX + "could not write the setup record {}; the next start decides the setup again", record.path(), e);
        }
    }

    /**
     * Checks a written {@code level-type} once every mod has registered its world types. This runs
     * at post-initialization, which still comes before the server reads the line. When the type is
     * unknown, {@code server.properties} goes back to how it was before this start and the server stops.
     */
    public void onPostInit(FMLPostInitializationEvent event) {
        if (event.getSide().isServer()) {
            checkWorldType(new MinecraftWorldTypes());
        }
    }

    void checkWorldType(WorldTypeLookup worldTypes) {
        Entry levelType = writtenLevelType;
        if (levelType == null) {
            written = null;
            return;
        }
        Problem problem;
        try {
            problem = EntryChecker.checkWorldType(levelType, worldTypes);
        } catch (RuntimeException | LinkageError e) {
            // The registry is the game's; a failure there must still stop cleanly and undo the write.
            problem = new Problem(levelType.fileName(), levelType.lineNumber(), levelType.text(),
                    "the world type could not be checked (" + e + ")", "a registered world type");
        }
        if (problem == null) {
            written = null;
            writtenLevelType = null;
            return;
        }
        restore("an unknown world type");
        recordPending = false;
        report.undoApplied(SettingCategory.SERVER_LINE, "undone, server.properties is back as it was before this start");
        stop(Collections.singletonList(problem));
    }

    private void restore(String reason) {
        try {
            written.restore();
        } catch (IOException e) {
            logger.error(StartReport.PREFIX + "could not restore {} after {}", serverProperties, reason, e);
        }
    }

    private void stop(List<Problem> problems) {
        report.problems(SettingCategory.SERVER_LINE, problems);
        report.log(logger, SettingCategory.SERVER_LINE);
        blocker.stopBeforeWorldLoad(problems);
    }
}
